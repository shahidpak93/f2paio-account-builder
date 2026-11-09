# F2P methods and human demonstration data in 0.13.0

## Installed routes

The catalogue contains 115 method definitions. This release adds 34 entries, with the existing air route also moved into the production data manifest. The manifest has 35 entries. It defines item IDs, access gear, minimum unnoted stock, skill gates, location, operation and expected outputs. Java binds each row to a supported native operation. Recipe chains can use the new methods as producers or as the final goal.

- Cooking adds anchovies, sardines, herring, pike, swordfish, beef, chicken and wine.
- Crafting adds six soft-leather items and four gemstone cuts. A leather method can use a tanning dependency when it needs to make leather from cowhide.
- Smithing adds silver, steel, gold, mithril, adamantite and runite smelting plus five metal dagger tiers. Steel and higher smelting verify the recipe-specific coal minimum before dispatch.
- Runecraft now registers air, mind, water, earth, fire and body. The runner checks the matching access item or worn tiara and verifies arrival at the altar before attempting to craft.

| Training skill | Registered routes |
|---|---:|
| Attack | 4 |
| Cooking | 14 |
| Crafting | 13 |
| Firemaking | 5 |
| Fishing | 8 |
| Magic | 4 |
| Mining | 9 |
| Prayer | 2 |
| Ranged | 4 |
| Runecraft | 6 |
| Smithing | 14 |
| Woodcutting | 4 |

Quests and standalone loot entries are excluded from that table. The catalogue still lacks some F2P guide methods, including Camdozaal activities, boss fights, and specialized furnace and anvil methods. Listing a guide section does not create a runnable capability.

## Downloaded sources

- Eighteen source revisions and their guide sections are indexed in [f2p-source-revisions.json](knowledge/f2p-source-revisions.json) and [f2p-guide-index.json](knowledge/f2p-guide-index.json). Full source revisions are in the local research cache under work/research/f2p-and-demonstrations.
- [f2p-items.json](knowledge/f2p-items.json) contains 818 tradeable F2P item records from the OSRS Wiki mapping. The gateway exposes guide and item lookup. The smoke test retrieved the Runecraft guide and the Rune essence ID.
- The demonstration importer downloaded seven public Minecraft contractor action recordings from OpenAI VPT. It sampled 510 transitions across high-level phases. Five recordings trained a Markov predictor and two tested it. On 54 held-out transitions, its mean log loss was 0.957, versus 1.145 for a predictor that ignores the preceding phase.
- The plugin loads [human-workflow-prior.json](knowledge/human-workflow-prior.json) only when its held-out check passes. It uses the learned work-continuation probability as a small score bonus for stocked, nearby eligible training. Raw actions, camera movements and timings are excluded from this score. The evaluation measures phase prediction in Minecraft and does not establish an OSRS performance gain.

Refresh public sources with python knowledge/refresh-f2p-data.py. Files already in the local download cache are reused.

## What the robotics research adds

- [RoboCat](https://deepmind.google/blog/robocat-a-self-improving-robotic-agent/) starts new skills with human-controlled demonstrations, then adds practice data back to the training set. The comparable improvement here would be to start with OSRS demonstrations tagged with actual skill and item outcomes and retain separately evaluated practice runs.
- [Physical Intelligence RECAP](https://www.pi.website/blog/pistar06) learns from demonstrations, interventions and autonomous practice. An account-builder equivalent would preserve what went wrong, the user's correction, and whether the bot later recovered from the same situation.
- [NVIDIA ASPIRE](https://arxiv.org/abs/2607.00272) generates, practices and revises robot skill programs, retaining verified skills for reuse. The existing chain library is a place to apply that idea with replay checks and explicit success criteria.
- [Open X-Embodiment](https://robotics-transformer-x.github.io/) and [DROID](https://droid-dataset.github.io/) are data sources for robot manipulation. Their observations and actions are appropriate to robot control. The imported human dataset here uses VPT gameplay and transfers only the broad workflow signal. [OpenAI VPT contractor demonstrations](https://github.com/openai/Video-Pre-Training#contractor-demonstrations)

## Verification and handoff

The installed JAR matches the build and the local gateway reports 0.13.0. The Java suite, native artifact loader, upstream Java compile and 30 agent tests passed. Replay checks include steel coal minimums, Body Altar access, a tanning prerequisite, and activation of the human-data score. Live production interfaces, altar transport and any OSRS performance gain remain unverified.

Start Microbot and check START version 0.13.0. The DECISION event reports the human workflow bonus next to the goal Q bonus. Watch CHAIN_VERIFIED and MATERIAL_SHORTFALL to distinguish completed production and missing inputs.
