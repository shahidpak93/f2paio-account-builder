# Player and automation research references

The reference corpus contains310 short attributed facts from93 distinct OSRS Wiki pages. This expansion fetched60 additional documents:58 were readable, two were unavailable, some redirected to existing pages, and irrelevant/disambiguation material was not turned into rules. Full fetched articles stay in work/research, outside the source package. This is guide/mechanics knowledge, not scraped private account data or human gameplay telemetry.

## Player progression

- [New player guide](https://oldschool.runescape.wiki/w/New_player_guide): skills, combat, quests, exploration and manageable objectives. Applied to category fairness and account-state planning.
- [Optimal F2P quest guide](https://oldschool.runescape.wiki/w/Optimal_quest_guide/Free-to-play): prerequisites and early XP rewards. Applied as evidence, not a mandate to perform only quests.
- [F2P melee training](https://oldschool.runescape.wiki/w/Free-to-play_Melee_training): weapon/style and target selection. Relevant to live stance mapping and combat readiness.
- [Jagex settings/setup guide](https://secure.runescape.com/m=news/new-player-guide-settings--set-up?oldschool=1): client controls and setup reference.
- [Stronghold of Security](https://oldschool.runescape.wiki/w/Stronghold_of_Security): independent one-time chest rewards and repeatable monster training. Native SOS emote varbits provide reward evidence. No new reward route is enabled or requested.
- [Adventure Paths](https://oldschool.runescape.wiki/w/Adventure_Paths): account eligibility and claim status matter; no assumption that every account has the same rewards.

All93 page URLs, revision IDs and source timestamps are in sources.json. Every fact in rules.json carries its source. PLAYER-POLICIES.json maps selected ideas to implementations. PLAYER-KNOWLEDGE.md is the expanded human-readable guide; TIPS-200.md is historical.

## Bots, AIO systems and agent frameworks

| Primary reference | Idea taken | Application / limit |
|---|---|---|
| [Microbot Hub](https://github.com/chsami/Microbot-Hub) and its debugging notes | Reuse current APIs and inspect live client state | Native quest helper, read-only diagnosis, real effect checks |
| [FOSS AIO DreamBot3](https://github.com/Dogcube51/FOSS-AIO-Dreambot3) | Activity branches selected from account state | Category planning and eligibility; its API/code was not pasted into Microbot |
| [DreamBot condition waits](https://dreambot.org/javadocs/org/dreambot/api/utilities/Sleep.html) | Wait for a condition with a deadline | Shared action settlement and receipts |
| [OSBot TEAMSSL F2P account builder](https://osbot.org/forum/topic/203899-aio-f2p-account-builder-teamssl/) | Persisted progression tasks across combat and skills | Planner design reference; advertised features are not independent performance evidence |
| [Voyager](https://github.com/MineDojo/Voyager) | Curriculum, reusable skills and feedback-driven revision | Declarative skill trials, evidence, promotion, quarantine and revision IDs; not Minecraft-style neural transfer |
| [Hermes skills](https://hermes-agent.nousresearch.com/docs/user-guide/features/skills) | Durable procedural skills and local tool use | Isolated OSRS developer profile, terminal/files/internet and reusable procedures |
| [RuneLite attack-style implementation](https://github.com/chsami/Microbot/blob/main/runelite-client/src/main/java/net/runelite/client/plugins/attackstyles/AttackStylesPlugin.java) | Read live weapon-style enum/struct metadata | CombatStyleResolver avoids hard-coded stance indices |

Ideas were adapted to this plugin and tied to verifiable state. Public scripts/marketing are not treated as proof that copied behavior works. No closed-source scripts were acquired. No new neural training occurred in this expansion; the facts are retrieved at runtime.

## Human-data and reinforcement-learning search

- [Wise Old Man player snapshots](https://docs.wiseoldman.net/api/players/player-endpoints): public tracked XP/kill-count snapshots can describe progression over time. They do not identify a player's exact movements, inventory choices or action policy. No individual player history was scraped for this release.
- [OSRS Wiki crowdsourcing](https://oldschool.runescape.wiki/w/RuneScape:Crowdsourcing): community observations support game-mechanic estimates such as skilling success rates. These aggregated mechanics inform the wiki training guides; they are not complete human play trajectories.
- [OSRS PvP reinforcement-learning project](https://github.com/Naton1/osrs-pvp-reinforcement-learning): a separate specialised simulation/training project, not an account-builder dataset or a drop-in controller for this plugin. Its learning scope should not be confused with account progression or the existing outcome-value learner.

The search did not establish a suitable public end-to-end OSRS human account-building action dataset that could simply be downloaded and trained into this bot. Guide knowledge, snapshots, aggregate rates and simulator-generated actions are different data types. The implementation therefore uses sourced progression knowledge plus its own verified outcome/chain evidence, rather than claiming to imitate recorded expert players.
