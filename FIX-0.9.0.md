# F2P AIO 0.9.0

The engine now monitors progress before entering supplies, chains, gear or native quests. It detects two minutes of idle unchanged inventory, repeated coin losses without account progress, and six minutes without XP or quest-state progress. Manual pauses shift deadlines. Supply/chain failures also notify the supervisor. The offending goal is deferred immediately.

A separate incident channel sends state, inventory, bank memory, levels, quests and recent logs to the local gateway. The workflow runs a typed verifier stop contract, researcher evidence retrieval, a planner model request and a developer model request for a proposed repair and regression case. Available live Microbot observations include dialogue, inventory, bank, NPCs and objects. Each response or error is recorded. Advice never executes generated code.

Newly selected goals receive a fast verifier review of registered capability, level and affordability. The engine waits for that review; a rejection defers the goal. Gateway failure retains Java preconditions as fallback. This is typed rule verification, rather than another model declaring itself correct. Existing action-specific effect checks remain; the model does not review every click.

The panel reports supervisor state and learned context count. Debug events include AGENT_CONTACT, AGENT_PREFLIGHT, SUPERVISOR_INCIDENT, AGENT_REVIEW and ML_UPDATE. The incident channel has independent worker capacity and a 110-second request bound; ordinary planner requests use 45 seconds. Model work remains bounded, with errors retained in reports.

AccountMilestones persists affordable supported skill targets at 10, 20, 40, then increments of ten. Ready quests take priority. This is not yet a full dependency graph for every quest ingredient or gathering route.

OutcomeLearning updates per-account expected rewards from observed XP, completed quests, active elapsed time and coin spending. Its bounded ranking bonus supplements the existing contextual Thompson sampler. Manual pauses are excluded and Skip is not failure evidence. Values and replay episodes persist. Chain promotion and quarantine remain separate.

OfferJournal records account-local submitted slot, item, quantity, price, direction and receipt state. Startup reports recorded offers and existing native offers remain protected. Full automatic settlement after restart is still unfinished; the journal does not adopt or cancel unknown offers.

## Startup error

The supplied exception at F2pAioScript.run line 62 was the saved break guard. The cooldown file recorded a break ending at 10:23:53 local time on November 8. Attempts before then threw an exception whose message was absent from the debug trace. The script now shows START_DEFERRED and the break end as a normal status. It respects the break. The unrelated native exit-portal warning is unchanged.

## Learning scope

The new learner improves selection among supported high-level activities. It cannot invent an executor, learn arbitrary walking mechanics, master PvP, or continuously retrain the neural model. The previously trained 0.8B checkpoint remains unchanged.

Learning-to-walk demonstrations usually use reinforcement learning with repeated resets and defined observations, actions and rewards. There is no equivalent resettable OSRS training environment in this release. Reference: https://gymnasium.farama.org/tutorials/training_agents/

## Verification and startup

Read test-v09.log, agent_lab/agent-tests.log and gradle-v09.log for final checks. The live agent smoke uses a synthetic incident and read-only client observations, not gameplay. Early probes had timeout and truncated-JSON errors; the final team-smoke report records the result honestly.

Restart Microbot and confirm version 0.9.0. The local gateway is running; use StartAgents after reboot. Incident reports are under agent_lab/state/incidents. Tokens and runtime snapshots are excluded from the source bundle. Complete live account building remains unverified, and the original project bible is not complete.
