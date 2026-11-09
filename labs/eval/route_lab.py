"""Seeded benchmark for high-level supply and milestone choices. No client or game input."""
from dataclasses import dataclass, replace
from functools import lru_cache
import json
from pathlib import Path
import numpy as np

ACTIONS = ("withdraw", "buy", "gather", "train", "quest")
GAMMA = 0.94


@dataclass(frozen=True)
class State:
    bank: int
    cash: int
    supplies: int
    xp: int
    distance: int
    delay: int
    iron: bool


def start(seed):
    rng = np.random.default_rng(seed)
    return State(int(rng.integers(3)), int(rng.integers(5)), 0,
                 int(rng.integers(3)), int(rng.integers(1, 5)),
                 int(rng.integers(1, 5)), bool(rng.integers(2)))


def choices(state):
    return np.array([
        state.bank > 0 and state.supplies < 2,
        state.cash >= 2 and not state.iron and state.supplies < 2,
        state.supplies < 2,
        state.supplies > 0 and state.xp < 3,
        state.xp >= 3,
    ])


def step(state, action):
    if not choices(state)[action]:
        raise ValueError("ineligible action")
    if action == 0:
        return replace(state, bank=state.bank - 1, supplies=state.supplies + 1), -2, False
    if action == 1:
        return replace(state, cash=state.cash - 2, supplies=state.supplies + 1), -state.delay - 1, False
    if action == 2:
        return replace(state, supplies=state.supplies + 1), -state.distance - 1, False
    if action == 3:
        return replace(state, supplies=state.supplies - 1, xp=state.xp + 1), -1, False
    return state, 20, True


@lru_cache(maxsize=40_000)
def lookahead(state, remaining):
    if remaining == 0:
        return -20, -1
    scores = []
    for action in np.flatnonzero(choices(state)):
        after, reward, done = step(state, action)
        future = 0 if done else lookahead(after, remaining - 1)[0]
        scores.append((reward + GAMMA * future, int(action)))
    return max(scores)


def baseline(state):
    legal = choices(state)
    return next(action for action in (4, 3, 0, 1, 2) if legal[action])


def features(state):
    return np.array([state.bank / 2, state.cash / 4, state.supplies / 2, state.xp / 3,
                     state.distance / 4, state.delay / 4, float(state.iron)])


class Network:
    def __init__(self, rng):
        self.w = rng.normal(0, 0.22, (7, 24))
        self.b = np.zeros(24)
        self.v = rng.normal(0, 0.20, (24, len(ACTIONS)))
        self.c = np.zeros(len(ACTIONS))

    def forward(self, x):
        hidden = np.tanh(x @ self.w + self.b)
        return hidden, hidden @ self.v + self.c

    def learn(self, x, gradient, hidden, rate):
        inner = (gradient @ self.v.T) * (1 - hidden**2)
        self.w -= rate * (x.T @ inner) / len(x)
        self.b -= rate * inner.mean(axis=0)
        self.v -= rate * (hidden.T @ gradient) / len(x)
        self.c -= rate * gradient.mean(axis=0)

    def action(self, state):
        scores = self.forward(features(state))[1]
        return int(np.argmax(np.where(choices(state), scores, -np.inf)))

    def copy_from(self, other):
        for name in ("w", "b", "v", "c"):
            setattr(self, name, getattr(other, name).copy())


def train(seed=92):
    rng = np.random.default_rng(seed)
    online, target, clone = [Network(rng) for _ in range(3)]
    samples, demonstrations = [], []
    for index in range(1100):
        state = start(index)
        for tick in range(10):
            oracle = lookahead(state, 10 - tick)[1]
            demonstrations.append((features(state), oracle))
            legal = choices(state)
            epsilon = max(0.15, 1 - index / 750)
            action = int(rng.choice(np.flatnonzero(legal))) if rng.random() < epsilon else online.action(state)
            after, reward, done = step(state, action)
            samples.append((features(state), action, reward, features(after), choices(after), done))
            if len(samples) >= 64:
                batch = [samples[i] for i in rng.integers(len(samples), size=64)]
                x, a, r, nxt, mask, terminal = (np.array(col) for col in zip(*batch))
                future = np.max(np.where(mask, target.forward(nxt)[1], -np.inf), axis=1)
                expected = r + GAMMA * future * ~terminal
                hidden, q = online.forward(x)
                gradient = np.zeros_like(q)
                rows = np.arange(len(a))
                gradient[rows, a] = np.clip(q[rows, a] - expected, -5, 5)
                online.learn(x, gradient, hidden, 0.035)
            state = after
            if done:
                break
        if index % 16 == 0:
            target.copy_from(online)
    for _ in range(1400):
        batch = [demonstrations[i] for i in rng.integers(len(demonstrations), size=64)]
        x, label = (np.array(col) for col in zip(*batch))
        hidden, logits = clone.forward(x)
        weights = np.exp(logits - logits.max(axis=1, keepdims=True))
        gradient = weights / weights.sum(axis=1, keepdims=True)
        gradient[np.arange(len(label)), label] -= 1
        clone.learn(x, gradient, hidden, 0.075)
    return online, clone


def learn_dynamics(seed=81):
    rng = np.random.default_rng(seed)
    observed, paths = {}, {}
    for index in range(1100):
        state = start(index)
        path = []
        for _ in range(10):
            action = int(rng.choice(np.flatnonzero(choices(state))))
            result = step(state, action)
            observed[state, action] = result
            path.append(ACTIONS[action])
            state, _, done = result
            if done:
                key = " > ".join(path)
                paths[key] = paths.get(key, 0) + 1
                break

    @lru_cache(maxsize=40_000)
    def search(state, remaining):
        if remaining == 0:
            return -20, -1
        candidates = []
        for action in np.flatnonzero(choices(state)):
            transition = observed.get((state, action))
            if transition is None:
                continue
            after, reward, done = transition
            future = 0 if done else search(after, remaining - 1)[0]
            candidates.append((reward + GAMMA * future, int(action)))
        return max(candidates, default=(-20, -1))

    def policy(state):
        action = search(state, 10)[1]
        return baseline(state) if action < 0 else action

    common_paths = sorted(paths.items(), key=lambda pair: -pair[1])[:5]
    return policy, len(observed), common_paths


def evolve(seed=19):
    rng = np.random.default_rng(seed)
    def policy(weights):
        def action(state):
            if state.xp >= 3:
                return 4
            if state.supplies > 0:
                return 3
            predictions = np.array([weights[0], weights[1]+weights[2]*state.delay,
                                    weights[3]+weights[4]*state.distance])
            return int(np.argmax(np.where(choices(state)[:3], predictions, -np.inf)))
        return action

    champion = np.array([3, 2, 0, 1, 0], dtype=float)
    seeds = list(range(1500, 1596))
    quality = evaluate(policy(champion), seeds)["mean_discounted_return"]
    for _ in range(240):
        child = champion + rng.normal(0, 0.45, len(champion))
        score = evaluate(policy(child), seeds)["mean_discounted_return"]
        if score > quality:
            champion, quality = child, score
    return policy(champion)


def evaluate(policy, seeds):
    completed, utility, total_steps = 0, 0.0, 0
    for seed in seeds:
        state = start(seed)
        for tick in range(10):
            state, reward, done = step(state, policy(state))
            utility += reward * GAMMA**tick
            total_steps += 1
            if done:
                completed += 1
                break
        else:
            utility -= 20 * GAMMA**10
    return {"completion": completed, "cases": len(seeds),
            "mean_discounted_return": round(utility / len(seeds), 3),
            "mean_actions": round(total_steps / len(seeds), 3)}


def main():
    dqn, clone = train()
    learned, transitions, paths = learn_dynamics()
    evolved = evolve()
    seeds = list(range(50_000, 50_256))
    result = {name: evaluate(policy, seeds) for name, policy in (
        ("bank_first_heuristic", baseline), ("deep_q", dqn.action), ("imitation", clone.action),
        ("learned_transition_search", learned), ("evolved_rules", evolved),
        ("known_model_search", lambda state: lookahead(state, 10)[1]),
    )}
    result["learned_transition_count"] = transitions
    result["common_successful_workflows"] = paths
    result["scope"] = "Synthetic supply decisions. Test RNG seeds are held out, with overlapping discrete state values possible. No live-policy promotion."
    path = Path(__file__).with_name("route-lab-results.json")
    path.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
