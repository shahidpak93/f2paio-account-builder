# What's new — setup summary (2026-11-08)

This file separates two very different things so there is no confusion:

1. **What changed inside the bot** (the plugin jar you load in Microbot) — currently **0.9.1**.
2. **What was set up outside the bot** (the research lab used for training/evaluation) — cloned and
   verified, but **not** shipped in the jar.

---

## 1. What's new inside the bot (0.9.1)

### Run energy — the "why doesn't it use the run button" bug

**Cause.** `F2pAioScript.run()` forced `Microbot.enableAutoRunOn = false` at session start. That
switch is what Microbot's inherited `Script.run()` and the walker use to click the run orb. The
plugin's own walk helper only tried to drink a carried "Energy potion" (members-only), so in F2P it
never did anything. Every trip was walked at roughly half speed.

**Fix.** New `RunEnergy` class owns the orb policy:

- long trip (>= 10 tiles) with >= 20% energy -> turn run on
- short repositioning -> no click
- energy below 20% -> walk this trip, log why, run on a later trip
- dialogue / bank / shop / GE open -> no click (that click steals interface focus and can break
  quest dialogue or GE offers)
- once on, stays on so it resumes as energy regenerates; clicks spaced >= 3 s so a failed click
  cannot loop

Also changed:

- Microbot's inherited auto-run is no longer disabled (it already guards blocking interfaces), which
  covers native quest-script walking during delegate. Your Microbot setting is respected.
- `GameState` now snapshots `runEnergy` / `runEnabled`; the heartbeat logs `run=on/off NN%`; the
  travel status shows `(run NN% on)`; debug events `RUN_STATE`, `RUN_ENABLE`, `RUN_HOLD` explain
  every decision.
- `AutonomousPlanner` charges a slightly larger **bounded** travel penalty when energy is empty
  (max 15 points, only tilts close choices).
- 16 new offline run-energy cases; all previous suites still pass.

**Still unverified live:** the orb actually toggling in game and travel getting faster. That needs a
Microbot restart and a real trip.

### Carried over from 0.9.0 (already in this jar)

- Supervisor checks progress before supplies/chains/gear/quests and defers a stalled goal.
- Incident path sends state/inventory/bank/logs to the local gateway; verifier, researcher, planner
  and developer roles respond; advice only, no code execution.
- Verifier preflight for every newly selected goal (typed level/budget contract).
- `OutcomeLearning`: per-account value updates from observed XP, quests, coins and elapsed time.
- `AccountMilestones`: persisted 10/20/40 skill targets; ready quests take priority.
- `OfferJournal`: records submitted GE offers and their receipt state.
- Startup break-guard now reports `START_DEFERRED` instead of an opaque `IllegalArgumentException`.

### Version and artifact

- Version: **0.9.1**
- Installed jar: `C:\Users\shahi\.runelite\microbot-plugins\F2pAioPlugin.jar`
- SHA-256: `97F967224F994CE62BFD00D47AC98FC9ECE087294D43AAD7912CAA13BFD0E5FE` (installed == built)
- Package: `outputs/F2pAio-0.9.1.zip`

---

## 2. What was set up outside the bot (the lab)

Location: `outputs/f2paio/labs`

This is **not** loaded by Microbot. It is the sandbox for teaching the planner faster and for
measuring it. 20 repos, 20 commits pinned, ~1.4 GB.

| Component | State here |
|---|---|
| `RLScape` (Gymnasium OSRS-like env) | **working**: 85/85 tests, client+server build, runtime installed, warm reset **2.37 ms**, full Gym reset **121 ms** median |
| `rs-sdk` (2004scape emulator + agent SDK) | dependencies installed (225 packages via bun); server not started |
| `rs-bench` / `jevscape` (RuneBench) | cloned; blocked by missing Docker |
| `osrs-pvp-rl` (RSPS + Gym + PPO + self-play) | cloned; setup not attempted (needs its own environment) |
| `osrs-agent-rag` (in-client RAG assistant) | cloned; needs Postgres/pgvector + LLM key |
| `OSRS-MCP-Companion` (live state over MCP) | **compiles** (65 classes) |
| `runescape-wiki-mcp` (wiki/GE MCP) | cloned; blocked by missing Docker |
| `flipping-copilot` (GE flipping AI) | **compiles** |
| `OsrsBot` (open bot API, GPL-3.0) | reference only |
| `openevolve` (evolutionary code search) | **installed**, import OK 0.4.1 |
| `agent-lightning` (agent RL trainer) | cloned; heavy deps not installed |
| `Voyager`, `Cradle`, `Player-1`, `EvoCUA`, `Shinken` | cloned; need Minecraft/games/GPU/Rust to run |

Toolchain added: Maven 3.9.9 (`labs/tools`), bun 1.4.4, lab venv with Gymnasium 1.4.0.
Missing and blocking: Docker, cargo, GPU.

Docs: `labs/SETUP-RESULTS.md` (commands + numbers), `labs/INTEGRATION.md` (how each lab maps to the
plugin and the phased plan), `labs/QUICKSTART.md`, `labs/README.md`, `labs/status.json`.

---

## 3. How to verify the bot now

1. Restart Microbot (and RuneLite) so it rescans `microbot-plugins`.
2. Confirm the F2P AIO plugin reports **0.9.1**.
3. Start a session with a destination >= 10 tiles away and >= 20% energy.
4. Expected debug events: `RUN_STATE`, then `RUN_ENABLE: energy=...% tiles=... observed=true`; the run
   orb turns on; travel status shows the run percentage; heartbeat includes `run=on NN%`.
5. If energy is low: `RUN_HOLD` -> walked -> `RUN_ENABLE` on a later trip.
6. If a dialogue/bank/GE is open: `RUN_HOLD reason=blocking interface owns input` and **no click**.

Agent gateway: `http://127.0.0.1:8092/health` is up and ready.

---

## 4. What is not done yet (honest)

- Run-orb behavior is offline-tested only; no live trip has confirmed it.
- The lab policy is not trained or wired into the plugin. `AutonomousPlanner` still uses heuristics;
  the bandit/outcome learners still have very little live data.
- Docker/Rust/GPU-dependent labs cannot run on this machine.
- Emulator labs (2004/2006 content) do not transfer 1:1 to live OSRS.
- Unlicensed repos in the lab (RLScape, rs-bench, jevscape, osrs-pvp-rl, osrs-agent-rag,
  osrs-bot-functions) must not be copied into the plugin.

## 5. Recommended next step

Phase 1 of `labs/INTEGRATION.md`: build the **account-builder evaluation** on the working RLScape/rs-sdk
harness (score QP + skill levels + coins, not peak XP), record the current heuristic as the baseline,
then wire a Voyager-style curriculum into `AccountMilestones`. That makes "smarter" measurable before
any policy is trained.
