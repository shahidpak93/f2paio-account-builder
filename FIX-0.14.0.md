# Route comparison and recovery in 0.14.0

## Active behavior

The production chain executor compares the eligible direct supply route and generated prerequisite chains against the same current account snapshot. It estimates preparation cost from inventory, bank stock, notes, market prices, travel and prerequisite work. It includes prior verified chain failures in the ranking. The selected plan runs through the existing action verifier. The estimate never substitutes for observed materials or XP.

When a production supply branch stalls or fails to produce its required item, the runner may try one different branch for the same goal. It uses a fresh account snapshot, retains materials already obtained and excludes a failed producer for that attempt. A failure in the final production action still follows normal incident recovery. A pending owned GE offer remains owned by the supply service.

The plan view shows the selected route and its estimated costs. ROUTE_COMPARE logs the competing route scores. CHAIN_REPAIR records a same-goal recovery attempt. Estimated cost is a ranking heuristic.

## Reference code reviewed

- [Naton1 OSRS PvP RL](https://github.com/Naton1/osrs-pvp-reinforcement-learning) was present locally and fetched at upstream commit 6914425d018ae36ef8cbaebaf1a3ba06dcc89921. The author separates simulation from execution and uses PPO with self-play and baseline evaluation. We adapted that design principle by comparing branches on one observed snapshot and replaying alternate starting states. The project targets PvP and its live plugin is absent from the public repository. Its training subtree carries GPLv3. No PvP code or model was copied into the plugin.
- [FOSS AIO DreamBot3](https://github.com/Dogcube51/FOSS-AIO-Dreambot3) was cloned at c96dccdaa0ff2bc894531eacb5035d09405af61f. Its activity, branch and leaf structure is a reference for composing capability steps. Its current README describes a prototype with limited implemented routes. No tracked license file was present in the reviewed commit.
- [Josh Cannella's DreamBot project](https://github.com/joshcannella/osrs-bot) was cloned at 5e6a700d551c3b49925ea7070928e83e61baaf8b. Its project layout is another reference for separating scripted execution, game knowledge and development tools. No tracked license file was present in the reviewed commit.

## Verification and handoff

Offline tests exercise the same route selector used by ChainRuntime. An expensive exchange detour loses to nearby oak gathering, observed bank stock favors direct preparation, a failed producer is excluded, and the recovery limit is enforced. Native artifact loading, upstream Java compilation and 30 gateway tests passed. The installed JAR and gateway report 0.14.0. Restart Microbot to load the new JAR. Live gameplay and PvP model transfer remain unverified.

For further development, RouteForecast owns branch estimates, ChainLibrary ranks the plans, and ChainRepair owns the one-retry policy. ChainRuntime wires all three into the executor. The trained text and vision candidates are documented in [the offline training handoff](training/osrs-v014/README.md).
