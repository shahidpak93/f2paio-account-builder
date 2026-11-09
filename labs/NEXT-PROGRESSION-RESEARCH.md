# Research on the next account-builder upgrade

## What the current code leaves open

- RecipeChains links up to two gatherers or public-drop sources to a single production goal. It cannot plan through several skill thresholds, quest gates, equipment unlocks or competing input sources.
- OutcomeLearning keys observations by method, ten-level band and whether cash is at least 500 GP. Two runs can establish a rate even if the gear, bank route, world or supply situation differed. Keep the number of observations, failure frequency and variability alongside XP per active hour.
- GearUpgrades uses a catalogue rank and budget checks. It does not estimate how much a loadout changes time to kill, damage taken or food consumption.
- The registered catalogue has no repeatable Smithing or Runecraft training method. Quest rewards alone cannot sustain progression in those skills.

## Recommended implementation order

1. Give each capability typed inputs, unlock conditions, effects, expected duration and evidence of success. Use a bounded hierarchical planner to compare bank stock, purchase, gathering and alternative goals. Keep the existing executor and replan from observed state after a failed step. Start with one mixed-skill chain, including an unavailable supply, and check that completed quests stay completed.
2. Add two missing vertical routes. Use The Knight's Sword as an early Smithing milestone and follow it with repeatable furnace and anvil actions. For Runecraft, verify altar access and essence independently, then start with air runes. Rune Mysteries gates access to the essence mine rather than the air altar itself. Source game-specific requirements from the current wiki.
3. Compare affordable, unlocked loadouts against several suitable monsters using time to kill and damage taken. Include banking, food, ammunition and travel in XP per wall-clock hour. Treat the OSRS Wiki DPS calculator as a reference for mechanics and tests. Preserve the existing cash reserve and allow inexpensive owned gear to win.
4. Add seeded scenarios on top of the existing account score and Java replay tests. Compare equal starting snapshots for a fresh account, a geared account, a low-cash account, an Ironman and a previously completed quest. Record milestone time, successful skill XP, idle time, deaths and net supply cost. Score time spent progressing across the session. RuneBench is a useful reference for isolation, but its peak short-window XP and gold objectives measure something different.
5. Add learning in stages. Log candidate eligibility and the reason for the winner. Use decision-only shadow runs to detect ranking regressions. Use simulator rollouts to measure outcomes for choices the installed planner never took. Then improve contextual route selection using verified experiences. A learned policy must outperform the existing policy on held-out scenarios before promotion. Hermes can draft and test variants against those fixtures, keeping the incumbent available for rollback.

RLScape already exists in the local lab. Its recorded build and reset smoke tests establish a starting point for simulation. The environment represents a RuneScape variant, so check mechanics against current OSRS and verify them separately before trusting transfer. The present request was research. No plugin or model changes were made.

## References

- [SHOP2 hierarchical planning](https://research.ibm.com/publications/shop2-an-htn-planning-system)
- [Unified Planning problem representation](https://unified-planning.readthedocs.io/en/v1.0.0/problem_representation.html)
- [Voyager skill verification and curriculum](https://arxiv.org/abs/2305.16291)
- [OSRS Wiki DPS calculator source](https://github.com/weirdgloop/osrs-dps-calc)
- [OSRS Wiki price feed](https://oldschool.runescape.wiki/w/RuneScape%3AReal-time_Prices)
- [Ironman Smithing guide](https://oldschool.runescape.wiki/w/Ironman_Guide/Smithing)
- [Free-to-play Runecraft training](https://oldschool.runescape.wiki/w/Free-to-play_Runecraft_training)
- [Rune Mysteries](https://oldschool.runescape.wiki/w/Rune_Mysteries)
- [RLScape](https://github.com/StevenDavenport/RLScape)
- [RuneBench](https://github.com/MaxBittker/runebench)
- [Contextual bandit replay evaluation](https://arxiv.org/abs/1003.5956)
- [Conservative offline reinforcement learning](https://arxiv.org/abs/2006.04779)
