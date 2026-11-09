# F2P method research and implementation status

The runtime registry is exported from Java into `METHODS.json`, rather than maintaining a separate invented feature list. Version 0.3 has **48 executable definitions**; none has been verified as a complete live money-making run in this version.

## Implemented catalogue

| Category | Definitions |
|---|---|
| Woodcutting | Trees, oak, willow, yew |
| Mining | Copper, tin, iron, clay, silver, coal, gold, mithril, adamantite |
| Fishing | Shrimp/anchovies, trout/salmon, sardines, herring, pike, lobster, tuna, swordfish |
| Basic combat | Chickens, cows |
| Quests through native Quest Helper | Cook's Assistant, Sheep Shearer, Doric's Quest, Witch's Potion, Rune Mysteries, Romeo & Juliet, Pirate's Treasure, Imp Catcher |
| Public collection | Ashes, cowhides, bones, feathers, discarded fish, raw beef, raw chicken, eligible PvP drops |
| Processing/cooking | Chocolate grinding, gold amulet stringing, soft/hard leather tanning, cooking shrimp/trout/salmon/tuna/lobster |

Locations and actions are typed definitions, not a promise of profitable throughput. Inputs, skill gates, trade restrictions, market liquidity, crowding and native interaction behavior affect feasibility. The controller uses current level/completion/resource state before selecting work.

## Broader research inventory

The [OSRS Wiki F2P money-making index](https://oldschool.runescape.wiki/w/Money_making_guide/Free-to-play) also lists jewellery crafting, smelting/superheating, alchemy, pies/dough, dyes, Bones to Bananas, shield collection, packs/shop resale, rune and Wilderness spawns, ogresses and giants. These require distinct recipes, prerequisites, inventory policies or combat profiles; they are researched additions, not active handlers in this release. Prices and quoted hourly returns are not hardcoded from that page.

[Collecting ashes](https://oldschool.runescape.wiki/w/Money_making_guide/Collecting_ashes) and [collecting/tanning cowhide](https://oldschool.runescape.wiki/w/Money_making_guide/Collecting_and_tanning_cowhide) informed the early funding/processing choices. Existing Microbot-Hub gathering and NPC Tanner source supplied API and interaction patterns; see SOURCES.md for pinned commits and licenses.

PvP world choice comes from Microbot's live world service. The implementation filters for eligible F2P PvP worlds and ranks by population; it does not copy a popular world number from a forum. Loot must be public according to the client's ownership model. The implementation is scavenging, not an automated PvP fighter.

This is a broad starting library, not an exhaustive enumeration of every item/location/recipe combination possible in F2P. Add researched methods only after a handler, prerequisites, recovery path and verification evidence exist.
