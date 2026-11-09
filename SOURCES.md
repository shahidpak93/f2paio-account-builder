# Sources, reuse and modifications

The target is Microbot. No closed Vortex code was copied, and none of the installed third-party plugin binaries were decompiled.

## Pinned sources

- [Microbot](https://github.com/chsami/Microbot/tree/71a586d5095e9999dea213e8b7d03a4e984c828c), commit `71a586d5095e9999dea213e8b7d03a4e984c828c`, cloned into `../../work/Microbot` relative to this project.
- [Microbot-Hub](https://github.com/chsami/Microbot-Hub/tree/50be835858a60d83346d8d7b7deaf2e373541e49), commit `50be835858a60d83346d8d7b7deaf2e373541e49`, cloned into `../../work/Microbot-Hub`.
- Installed runtime/compiler dependency: `C:\Users\shahi\.microbot\microbot-2.6.54.jar`. Its actual SHA-256 is recorded in `dist/build-info.json`.
- User attachment preserved verbatim as `PROJECT-BIBLE-v3.1.md`.

## Reused components

| Source | Use and modifications |
|---|---|
| Hub `woodcutting/enums/WoodcuttingTree.java` | Adapted F2P tree names, products, minimum levels and actions into `Methods.java`; removed Lombok and members entries |
| Hub `woodcutting/enums/WoodcuttingTreeLocations.java` | Selected Lumbridge/Draynor anchors, adapted to small per-method definitions |
| Hub `mining/data/Rocks.java` and `MiningRockLocations.java` | Adapted copper/tin/iron names, products and level requirements, with a Varrock southeast anchor |
| Hub `autofishing/enums/Fish.java` and `FishingSpotLocation.java` | Adapted shrimp/trout products, tools, actions and locations |
| Hub gathering script implementations | Reviewed inventory-full, busy-player and return-to-location patterns; replaced their separate loops with one custom engine |
| Core Quest Helper definitions for Cook's Assistant, Sheep Shearer, Doric's Quest and Witch's Potion | Adapted prerequisites, recognized dialogue choices and Hetty stage handling into the four small quest flows |
| Core `Script`, cache/query layer, entity clicks, `Rs2Walker`, `Rs2Bank`, `Rs2Inventory`, `Rs2Dialogue`, `Rs2Player`, native mouse/input arbitration | Used directly from the installed Microbot runtime; not bundled or reimplemented |
| RuneLite/Microbot `NavigationButton` and `ClientToolbar` | Native toolbar entry opening a custom Swing window |

The original complete Hub scripts are not bundled unchanged: several are large, tightly coupled to their original plugins, and include unrelated members features. The custom engine owns scheduling, stop control and persistence, while Microbot continues to own interaction details.

## Version 0.3 additions

- `npctanner/npcTannerScript.java` supplied the Ellis location and soft/hard tanning widget references used by `ProductionActions`; its loop and random deposit-all behavior were replaced with the common controller and preconditions.
- Native `QuestHelperPlugin`, `QuestScript`, `DetailedQuestStep`, item requirements and QuestManager are now used as a runtime integration. The built-in quest loop owns quest actions; the controller can pause it for funding/supplies. Core registry implementations for Rune Mysteries, Romeo & Juliet and Pirate's Treasure are reused through that integration.
- Native `Rs2GrandExchange`, bank, inventory, equipment, world service and walker provide buying/selling, supply withdrawal, gear operations, world selection, transports and eligible teleports. These shared classes remain outside the plugin JAR.
- F2P method research and source links are maintained in `F2P-METHODS.md`; live-source market figures are not hardcoded as profit claims.

The modified subset and derivative constants are covered by the upstream BSD-2-Clause notice in `LICENSE-MICROBOT-HUB.txt`. The Microbot BSD notice is retained in `LICENSE-MICROBOT.txt`. Both are included in the project; the Hub notice is also included in the plugin JAR. Runtime libraries remain supplied by Microbot under their own licenses.

## Earlier chat context recovered

- **Build OSRS Account Plugin**, chat `6aecb471-bfa4-83eb-94c8-b59b7ac46f03`: the requested older design was an Account Builder with Barrows Gloves, Vorkath and Gargoyle goals. The prior assistant explicitly reported it could not create or compile a plugin. No older compiled implementation was recovered.
- **Build F2P Highlight Plugin**, chat `6aed057f-5a74-83eb-8675-c5c4265b8e0b`: discussion covered RuneLite highlights, AHK and Microbot interaction APIs. That is context, not a verified existing implementation of this AIO.
- **Microbot Overview**, chat `6aecebdf-f41c-83ed-987e-7244e7bfbfba`: read for surrounding context.

The attached v3.1 Microbot specification is the current target. Its historical BUILT labels are not evidence that those source files exist in this project.

## 0.3.1 provenance and freshness check (2026-11-08)

`git ls-remote origin refs/heads/main` matched both pinned Microbot and Hub commits above at the time of this repair. The binary compatibility target remains the locally installed Microbot 2.6.54. Repository freshness does not establish that every upstream implementation is correct.

`GroundLoot` is custom code, with the account ownership rule adapted from native `grounditems/GroundItemsPlugin.shouldDisplayItem(TAKEABLE)`. The custom planner, supply recovery, scan diagnostics, world policy and coin snapshot correction are maintained in this project. Complete public Hub scripts were not pasted unchanged. This is a custom plugin built on shared native APIs, not an independently written client or a guarantee about bans. There is no evidence here identifying any specific reused script as "banned."

## 0.6.0 learning and native flipping

The planner, persistent contextual bandit, skill-family rotation and probation verifier are custom project code. The bandit uses Thompson sampling with a standard Marsaglia-Tsang gamma sampler; no external agent framework, model weights or training dataset was downloaded. Optional proposal generation uses the project's existing bounded AI endpoint integration.

Native GE Flipper integration follows the installed FlipperPlugin.jar and reviewed Hub `geflipper/FlipperPlugin.java` / `FlipperScript.java` lifecycle. It delegates to the existing Copilot/Flip Assist plugin and graceful Finish API instead of copying subscription logic or guessing a configuration-file format. Existing native trade/exposure settings are retained. API signatures were checked against the installed JAR; live trading is unverified.

The source is custom and modified around native Microbot APIs. Public recipes/locations and native integrations are explicitly credited above. Repository age or customization does not establish ban status, and no cited script was proven "banned" in this task.

## 0.7.0 chain compiler and evolutionary policy

ChainDraft/Plan/Runner/Library/Runtime, replay fixtures, batch grouping and provider fallback are custom project code. Chain actions delegate to the same native Microbot APIs and existing registered method/production recipes. Evolution is bounded parameter mutation with empirical reliability/action-rate selection, probation and retained-parent fallback; method ranking retains the earlier contextual Thompson sampler. No EvoSkill, RuneBench, PPO, ONNX or vision framework was copied or claimed as installed.

Actual local-model output is preserved in `examples/local-model-chain.json` as a validated but unproven proposal. Installed Ollama tags and real inference were checked locally. Configured Gemini returned HTTP 404 during this session; no speculation about retirement/account status is required. Local requests are credential-free and do not receive the Gemini key.


## 0.8 additions

The 200 independently paraphrased short tips use40 OSRS Wiki pages, with URLs and actual revision metadata in knowledge/sources.json and per-tip references in knowledge/rules.json. Full cached source extracts remain in work, outside the package. Models use official Qwen3.5 weights (https://huggingface.co/Qwen/Qwen3.5-0.8B ; https://ollama.com/library/qwen3.5); PEFT/Transformers train the adapter and official llama.cpp converts the merged weights. The private adapter is newly trained; the2B planner is a system profile. Existing Microbot and hub license files remain included.
