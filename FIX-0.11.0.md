# Progressive account building in 0.11.0

The previous combat catalogue stopped at chickens and cows for melee and at chickens for ranged and magic. The planner could repeat a method for at least an hour, and an AI suggestion could replace the best-ranked choice.

- Combat now steps through chickens, cows, giant frogs and hill giants for melee, ranged and magic. Higher-risk targets require real combat stats, appropriate gear and food. Successful attack verification requires XP or item evidence.
- The same progression filter runs across registered training skills. Iron mining, willow woodcutting and fly fishing retain priority over higher-level materials that typically train more slowly. Sustained XP/hour from comparable account runs can favor a proven faster non-starter route. The planner checks for a better eligible route every 30 seconds and switches between fights or completed chains.
- The initial bank scan is bounded to 45 seconds. Loadout checks compare unlocked and owned equipment and use banked upgrades before GE buys. Purchases require a current price, sufficient budget after reserve and a meaningful catalogue rank gain. The rank comparison is a heuristic for F2P equipment value.
- Recipe chains compose registered gathering or public-drop capabilities with a matching recipe when its inputs are absent and the combined prerequisites are affordable. Examples include chopping and burning logs, fly fishing and cooking fish, and collecting and burying bones. Each stage requires its own evidence. Combat and native Quest Helper routes retain their dedicated execution and verification.
- The default review interval is the configured 15 minutes. Quests, combat and skilling continue to share the account curriculum. Training choices exclude zero-XP tanning and chocolate grinding in Account Builder mode.

## Start and inspect

Restart Microbot or rescan its local plugins to load the installed JAR. Press Start and check for version 0.11.0 in the debug export. The panel displays the active goal and the chain stage. Search the export for DECISION, UNLOCK_REPLAN, CHAIN_VERIFIED, REPLAN and GEAR_UPGRADE to see selection, verification and recovery. Skip remains available.

## Handoff

ProgressionPolicy owns the cross-skill shortlist. GoalGraph owns account and method eligibility. Engine rechecks the shortlist during a run. OutcomeLearning persists measured training rates by method and account context. RecipeChains compiles dependencies against ProductionRecipes and Methods, then ChainRunner verifies each block. GearUpgrades owns optional spending and uses GearCatalog for eligibility.

Offline regressions, native plugin loading, upstream compilation and 28 gateway tests passed. The installed JAR matches the build hash and the local gateway reports 0.11.0. Verify live transitions, GE purchases and multi-stage effects after reloading Microbot. This release has 77 registered method definitions. Additional OSRS skills, methods and dependencies need executable capabilities before the planner can include them.

[Free-to-play melee training](https://oldschool.runescape.wiki/w/Free-to-play_melee_training) · [Mining training](https://oldschool.runescape.wiki/w/Free-to-play_Mining_training) · [Fishing training](https://oldschool.runescape.wiki/w/Free-to-play_Fishing_training) · [Woodcutting training](https://oldschool.runescape.wiki/w/Free-to-play_Woodcutting_training) · [Firemaking training](https://oldschool.runescape.wiki/w/Free-to-play_Firemaking_training)
