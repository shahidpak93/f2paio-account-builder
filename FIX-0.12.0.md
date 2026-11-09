# Account planning and learning in 0.12.0

## What runs in the plugin

- GoalLearning learns delayed goal value from verified XP, level gains and quest completion. It uses a temporal-difference update at the next eligible goal selection and can change ranking among eligible goals. It keeps per-account context for levels, gear, travel, stock and quest progression. Manual skips produce no training reward.
- The Account Builder ranks goals with one learned value term. The old bandit scoring term is used outside Account Builder. Historical XP observations still inform route progression.
- UnlockPlanner works backward from a nearby unfinished quest to reachable level gates. AccountMilestones exposes the chosen gate and the executor reconsiders after reaching it. Quest Helper still owns native quest steps.
- RecipeChains searches multiple source paths across up to three prerequisite blocks. It compares travel and available supplies, and ChainRunner verifies the required unnoted item quantity before progressing. ChainLibrary still records evidence, evolves trial variants and quarantines failures.
- The catalogue adds bronze and iron smelting, bronze dagger forging and air rune crafting. The gathering, smelting and forging dependency sequence passed replay tests. Live furnace, anvil and altar interaction remains to be checked.
- The local planner can break a close tie using a bounded wait and a current eligible shortlist. Its gateway output is constrained to that shortlist. Research returns sourced retrieval directly. Verification uses rules directly. Both previously loaded a model and then replaced its output.

## Experimental lab

Run labs/.venv/Scripts/python.exe labs/eval/route_lab.py. The script executes deep Q-learning, behavior cloning, learned transition search, workflow extraction from successful trajectories, and evolutionary search. It compares them with a baseline and a known-model planner on 256 seeded synthetic supply scenarios. The environment is a small model of preparation decisions. It has overlapping discrete state values between training and testing, and it does not model live OSRS mechanics. No neural or evolved policy from this benchmark was promoted into the game.

The scores were 8.342 baseline, 8.392 deep Q, 8.136 imitation, 6.186 learned-transition search, and 8.437 for both evolved rules and exact lookahead. See [raw benchmark results](labs/eval/route-lab-results.json). These results measure the synthetic scenario only.

The implementation uses the underlying ideas from hierarchical planning, skill composition, evolution and temporal credit assignment. The full Dreamer 4, Agent Lightning, TRACE, AFlow, GEPA, AlphaEvolve, Decision Transformer and diffusion implementations were not added to the live stack. They require suitable action traces or training environments and evaluation beyond this benchmark.

## Installed configuration and verification

The installed JAR and gateway report version 0.12.0. Offline Java regressions, Microbot's native artifact loader, upstream Java compilation and 29 gateway tests passed. An authenticated gateway smoke test used the actual Ollama planner with two eligible options, returned six attributed research facts, and rejected a quest-locked loadout. The initial planner smoke test exposed an invalid response shape and the schema-constrained test passed after the fix.

The unused Ollama tag osrs-builder-fast:0.8b was removed after the research and verification roles switched. Its GGUF and Modelfile are retained for restoration. The active osrs-builder:2b planner, Hermes model and unrelated installed models were kept.

Open Microbot and start the plugin. Look for START version 0.12.0, Q_UPDATE, AGENT_CHOICE, CHAIN_VERIFIED and MATERIAL_SHORTFALL in the debug export. Live game control and long-term learning are unverified.

## References

[Q-learning](https://doi.org/10.1023/A:1022676722315) · [DQN](https://doi.org/10.1038/nature14236) · [SHOP2](https://research.ibm.com/publications/shop2-an-htn-planning-system) · [Bronze bars](https://oldschool.runescape.wiki/w/Bronze_bar) · [Air Altar](https://oldschool.runescape.wiki/w/Air_altar)
