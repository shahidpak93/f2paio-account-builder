# Current release: 0.9.4

Read [FIX-0.9.4.md](FIX-0.9.4.md) for account knowledge, combat/skilling balance and one-time rewards, then older patches. Older sections are historical.

# Latest patch: 0.8.2

Account-builder affordability and funding batch correction. See [FIX-0.8.2.md](FIX-0.8.2.md). Restart Microbot to load0.8.2; prior patch notes below are historical.

# Latest patch: 0.8.1

Purchase receipt verification and repeated-buy protection installed. See [FIX-0.8.1.md](FIX-0.8.1.md). Restart Microbot to load the patch; previous running instances retain0.8.0.

# Current AI handoff â€” 0.8.0

## Read first

The user now prioritizes account building over GP. They requested new small local reasoning models, actual OSRS fine-tuning, agents with computer/internet tools, 200 researched tips, bulk noted sales and eligible gear upgrades. Current setup and limits are in README, GUIDE, AI-SETUP and FEATURES. The historical 0.7 notes below are architecture history: their version, default flipping and primary Gemini configuration statements are superseded.

Workspace: `C:\Users\shahi\Documents\Codex\2026-11-08\s`. Project: `outputs/f2paio`. Java11/JDK17 against Microbot2.6.54. Keep source files below400 lines. Build/Test/Install scripts are entrypoints. Install migrates defaults to accountBuilder, local gateway8092, model2B, PvP/flip off while preserving unrelated settings/secrets. Never print tokens or keys.

## Current changes

ProgressionPolicy/GoalGraph/AutonomousPlanner drive quest/XP priorities, reserve quest ingredients and gate quest readiness. GearCatalog/GearUpgrades/EquipmentPolicy/CombatTraining select standard budget profiles, real level/quest locks, bank stock and market-priced affordable upgrades. No regional fence. ResourceItem canonical IDs distinguish note/item forms; GameState.count excludes notes. BankWithdrawalPolicy uses notes for funding, items for use. SaleBatches aggregates canonical IDs while physicalId supports store clicks. TradeService removes arbitrary10k cap and caches fresh quotes. Twenty native quest integrations and64 method definitions compile; actual game completion remains unverified.

KnowledgeRules loads200 tips embedded by Build.ps1; AgentBus injects relevant rules, authenticates only local8092 with token file and retains direct local fallback. Agent gateway has planner/researcher/verifier/developer profiles, web retrieval, scoped project/game access and draft compile/tests. Researcher publishes retrieved canonical evidence; verifier host rules override neural outputs. Code drafts never autoexecute/hotload. Explicit StartAgents launcher only; no plugin process autostart.

Actual CPU LoRA training produced adapter, reports and installed osrs-builder-fast:0.8b; osrs-builder:2b is a prompt profile only. Training data106/heldout30,53steps. Teacher-forced98.03% is NOT game accuracy. Bare/grounded generation has failures; see saved model benchmarks. Do not remove guards to make a model appear correct. No continuous weight learning; account-local selection/chain evidence remains the online learning mechanism.

## Evidence and next work

0.8 offline: 10,193 existing checks plus13 loot/coin/world,20 learning,97 chain and101 account-builder checks. Native artifact loading, full upstream client compile and13 Python agent tests passed. No live0.8 client play was available. Verify live notes/grouped GE fill, tool acquisition, gear unlock/budget, native quests, range/mage/prayer/firemaking, chain effect/promotion/reuse, world hop and pause handling. GE pending ownership is not durable across process restarts.

Source research cache: `work/research/wiki-pages.json`;40 source revisions in knowledge/sources.json. Base HF weights `work/hf-qwen08`; train environment work/train-env; mergedHF work/osrs-qwen08-merged; GGUF work/osrs-trained-q8.gguf; upstream client work/Microbot; llama converter work/llama.cpp. Huge bases stay outside package; adapter/dataset/reports included. Runtime token in user.runelite tree and agent_lab/state must not be bundled. Source ZIP excludes state/cache/compiled drafts.

Current docs and STATUS supersede the historical notes. Do not claim original bible complete, all200 tips executable, or AI unable to make mistakes.

---

# AI handoff â€” current F2P AIO 0.7.0

## Latest objective and result

User requested executable skill chaining, learning and reuse, evolving algorithms, missing features from pasted notes, error fixes and bulk selling. Implemented a typed chain compiler/runtime, persistent per-account chain evidence, bounded evolution and provider fallback. See FEATURES.md for explicit remaining gaps; do not claim arbitrary Java generation, PPO/ONNX training, vision, a full emulator or the whole bible complete.

Project remains `C:\Users\shahi\Documents\Codex\2026-11-08\s\outputs\f2paio`. Build/Test/Install scripts, Java 11 target, Microbot 2.6.54 and licenses remain as documented below. Installed version is 0.7.0. Source files remain under 400 lines.

## Architecture added in 0.7

- `ChainPlan`: typed PREPARE/TRAVEL/ACT/VERIFY blocks and optional final BANK/SELL. Max three blocks, 24 actions, 13 steps. Every action must have matching preceding preparation/travel and following verification. Only registered OBJECT/FISHING/GROUND/PROCESS capabilities accepted.
- `ChainDraft`: compiles compact AI `goal/methods/batch` JSON into that full contract. Goal must be the last capability. Inherits tools, levels, supplies and native execution from registered methods. Full typed drafts may also be validated. Unknown capabilities are rejected.
- `ChainRunner`: pure replayable state machine with a Backend interface, relevant XP/product verification, bounded repetitions, per-step/whole-chain deadlines and pause accounting. Animation or dispatch alone never verifies a chain. Handles backend exceptions as failed trials. If targets dry up after some verified actions, proceeds to settlement rather than discarding progress.
- `ChainGameBackend`: native adapters for supply recovery, world selection, nearby targets, entity/production actions, banking and batched sales. Reuses Engine-owned SupplyService/FundingTask so pending offers are not silently discarded on chain changes. Checks substep skill/tool restrictions.
- `ChainLibrary`: atomic account-local `<persona-hash>-chains.json` definitions/evidence. Three verified completed runs promote; two failures quarantine. Utility combines reliability and verified actions/minute; XP/cash stored separately. Normally picks the best promoted plan, explores a trial about 20% of eligible selections, mutates batch size/wait only after promotion, retains parent, max six variants per goal/depth three. Quarantined known synthesized baselines can retry after 20 minutes; AI/evolved variants stay excluded.
- `ChainRuntime`: chooses/runs/records one plan for the current goal, exposes plan/dashboard summary. Engine delegates supported kinds to it; combat/native quests retain established paths. Skip/reset clears runner; shared supply/sale services retain within-session ownership. Quest completion now explicitly exits native delegation.
- `DiscoveryCoordinator`: alternates method and chain drafting asynchronously every 45 minutes when AI enabled. `RecoverySupervisor` holds the previous bounded diagnostic recovery logic outside Engine.
- `AgentBus` / `LocalAiFallback`: compact chain drafting, larger output allowance for plans, credential-free localhost fallback after primary failure. Existing request budgets remain. Local fallback 60-second timeout runs on planner thread, not client/game thread. Offline fake-provider tests explicitly disable real local fallback.
- `SaleBatches` / `FundingTask`: group inventory slots by item ID, reserve ingredients and sell one total quantity per type. Keep GE open between batches. Engine settles an existing owned sale before new actions; SupplyService polls pending owned supply offers before changing acquisition.
- `GroundLoot`: nearest reachable candidates, two-minute item/world/tile backoff after rejected pending pickups. Successful earlier pickups are not blacklisted merely because a spot later has no target.
- `SessionTransitions`: up to 30 seconds for world-hop/loading/connection states rather than immediate logout stop. Manual/global/human pauses freeze chain deadlines. Script and panel expose plan and learning counts; debug export includes both.

## Evidence, setup and next live checks

Supplied attachment `1fe98896-01a9-4b4f-b118-c3d943d05e21/Pasted text.txt` showed repeated SELL 1 x Tin ore with many occupied inventory slots. Root cause was selecting one ResourceItem slot rather than summing all identical IDs. It also showed pending pickup timeouts, a manual input pause and an Anonymous rendering exception. Do not attribute that separate plugin's exception to this JAR.

Final checks: 10,193 existing checks, 13 loot/coin/world cases, 20 previous learning cases, 97 chain/replay/evolution/batch checks; native artifact loading and full upstream-client compile passed. The new tests include 64 deterministic policy replays, multi-capability sequencing, no action before supply readiness, actual-effect requirements, timeout/pause/transition handling, persistence, promotion, mutation and rollback. They are synthetic contract tests, not OSRS world simulation.

Real AI checks used synthetic account state only. The user's existing configured Gemini provider returned HTTP 404. Local Ollama listed `shahi-smart:4b`; a real local JSON response succeeded. A first verbose chain draft was rejected, then the compact capability compiler was introduced. A subsequent actual AgentBus request fell back to localhost, produced shrimp â†’ cook_shrimp, and passed the full plan validator. Saved as `examples/local-model-chain.json`; it is NOT a promoted game-tested skill. Preserve primary Gemini settings and DPAPI secret; saved config now enables the existing local fallback model. No key was printed, copied into source or sent to localhost.

No game client was running at final validation. Next: restart/confirm 0.7.0, Start auto, inspect Plan / learning. Verify bulk GE_SUBMIT quantity, actual fills and coin/inventory changes; follow public loot backoff and world arrival; validate fishâ†’cookâ†’bank or another multi-capability trial; observe three completed runs promote a plan, restart and verify reuse. Verify failed evolved variants retain the working parent. Run native Copilot handoff/Finish with existing configuration. Check pause/resume during VERIFY and world hop. Pending GE ownership is still not durable across process restarts; inspect/reconcile offers rather than assuming fresh ownership. Avoid widening validation or removing verifiers to make an invented AI draft appear valid.

`test-v07.log`, `STATUS.json`, `dist/build-info.json`, `FEATURES.md` and current guide carry evidence. Prior 0.6 notes below describe retained components, not current version or test count.

---

# Prior implementation notes â€” 0.6.0

## Read first

User wants full automatic F2P account progression, no activity checkboxes, an outcome dashboard, Skip, useful diagnostics, custom modified code, native Quest Helper/Copilot reuse and learning. Regional lock-in was explicitly rejected. Do not reintroduce area score bonuses or fences. Original specification: `PROJECT-BIBLE-v3.1.md`. Full bible remains incomplete.

Project: `C:\Users\shahi\Documents\Codex\2026-11-08\s\outputs\f2paio`. Main sources are `src/main/java/com/example/f2paio`. Java 11 bytecode built with JDK 17 against `C:\Users\shahi\.microbot\microbot-2.6.54.jar`. Run `Build.ps1`, `Test.ps1`, `Install.ps1`. Installed destination: `C:\Users\shahi\.runelite\microbot-plugins\F2pAioPlugin.jar`. Manifest version comes from Tuning.VERSION. Build hashes are in `dist/build-info.json` and `STATUS.json`.

Current source at the start of the latest continuation was 0.5.0 and included new regional planning, learning proposals, price-thread fixes, native pacing overrides and a separate price-based FlipTask. Those edits were preserved or corrected; do not restore old 0.3 code over them.

## Latest changes and ownership

- `AutonomousPlanner`: removed regional +/-120/140 biases (quests/loot had their own regional biases). Travel cost is bounded. Bandit samples are computed once per candidate, not on every comparator invocation.
- `TaskRotation`: skill-family cooldown, consecutive-session diversity and fallback when only one skill is available. `Engine`: Skip excludes the whole current skill family; timed rotation does not require banking; two same-skill bank/reselect cycles force another available family. Skip/rotation does not penalize the bandit.
- `Bandit`: actual fractional-shape gamma sampling, account-local atomic persistence, coarse level/cash contexts, separately stored XP/cash evidence and bounded decaying success/failure evidence. Engine initializes it from the persona hash and captures action baselines immediately before dispatch. This is online method selection, not a trained neural model or profit estimator.
- `SkillProbation` + `Methods`: strict supported proposal schema, builtin-ID protection, supported tools/actions and observable product requirements. Three verified effects promote; two rejections quarantine. Older learned definitions are revalidated on load. Generated PROCESS/QUEST/COMBAT definitions are not accepted because they lack generic executor contracts. Learned definitions can still be wrong; they are provisional until observed.
- `ActionMonitor`: learned methods require relevant XP/product growth; ordinary ground pickup requires product growth. Animation/dispatch alone is insufficient. No-target learned trials also count as rejections at abandonment. Quarantined methods are excluded from GoalGraph and current failed trials.
- `F2pAioActions`: nearby named NPC/fish/ground candidates can satisfy a task away from fixed anchors. Anchors remain fallback destinations. Preconditions and named-target matching remain enforced.
- `FlipTask`: replaced speculative price-based trader with a bridge to the installed `net.runelite.client.plugins.microbot.geflipper.FlipperPlugin`. Requires active Copilot/FlipAssist; does not alter its settings/secrets or take over an already-active native flipper. Checks private native `requestFinish()` signature before starting; invocation matches installed JAR and reviewed upstream implementation. Native Finish stops the plugin after settling orders. AIO waits for inactive state before resuming its own actions. Ten-minute/free-cash/Skip/Stop requests Finish. Start/Stop race is covered by a deferred Finish flag. Free-GP threshold is not a hard exposure limit; native settings own trading budget. Integration is compile/signature checked, not live verified.
- `AntiblockerTuner`: overrides only three pacing fields and restores exact captured values if still owned. Removed unrelated mouse/randomness/fatigue rewrites. Manual pauses and external scheduled breaks remain respected.
- Existing `TradeService.unitPrice` client-thread price lookup/fallback is retained. User log previously showed repeated off-thread item-definition exceptions in 0.4.0; current source had already fixed that before this continuation.
- Earlier loot fix retained: `GroundLoot` follows account-aware public OTHER ownership semantics, unlike `isLootAble()` which rejects all OTHER items. Live pre-fix probe saw cache=14/raw=14, including public OTHER-owned items; no cache registry workaround was needed. Ordinary scavenging/funding ranks normal F2P worlds by population. `GameState` sums coin stack quantities rather than counting slots.

## Verification and remaining work

Installed-client compile, native loader/manifest/Java 11 packaging, 10,193 existing checks, 13 loot/coin/world cases and 20 learning/rotation/verifier cases pass. Full upstream client compile passed after syncing custom sources. Source files remain below the bible's 400-line limit. Installed Flipper public getter and private Finish signatures were inspected via javap. Tests use isolated user.home, not the real bank/account or learning files.

Client was closed when final checks ran, so no claims of live pickup/hop/trading/long-session success. First next run: confirm panel 0.6.0, Start auto, verify copper Skip does not select tin; run through two bank cycles and inspect skill diversity; restart to verify real learning persistence. Test nearby fish away from anchor, public pickup quantity change and observed world hop. Test missing tool bank/GE/shop recovery and the retained price-thread fix. With authenticated active Copilot and sufficient funds, verify FLIP_DELEGATE, no overlapping AIO actions, native Finish and resumption. Check offers and native exposure settings during this test.

Other limitations: funding is opportunistic public loot and observed sales, not guaranteed income; external break handlers remain independent; Anonymous plugin errors in the attachment belong to that plugin; not every researched F2P method is implemented; optional LLM discovery needs a working endpoint and was tested at the verifier/schema layer rather than with a live provider. Learned model evidence is not a guarantee a proposed location is valid or F2P. Original method catalogue is 48 definitions. Recheck runtime APIs before upgrading Microbot.

## Source and history pointers

`SOURCES.md`, `WORKLOG.md`, `GUIDE.md`, `F2P-METHODS.md`, `METHODS.json`, `STATUS.json`, `test-v06.log` and `dist/build-info.json` carry current evidence.

Upstream checkouts: `C:\Users\shahi\Documents\Codex\2026-11-08\s\work\Microbot` at 71a586d5095e9999dea213e8b7d03a4e984c828c; adjacent Microbot-Hub at 50be835858a60d83346d8d7b7deaf2e373541e49. Sources and licenses are documented. Custom sources can be copied into runelite-client before `:client:compileJava`, using cached Gradle 8.10.2 and JDK 17.

Latest attachment `21049580-fd55-41a2-8ba2-b68a8cce320f/Pasted text.txt` contains user notes, a long client log and earlier suggested research. Treat its pasted research claims as proposals, not verified facts or implemented features. No neural training, vision model, self-generated executable Java or ban-proof claim was added.

0.9 implementation: IncidentDetector and AgentSupervisor monitor all engine phases. team_supervisor.py routes incident roles and selection reviews. OutcomeLearning owns saved value updates and episodes; AccountMilestones owns skill focus; OfferJournal records offers without adopting unknown ownership. Pending: complete item-source dependency graph, detailed quest-step progress, automatic restart offer settlement and live evaluation.
0.9.1 implementation: RunEnergy owns the run-orb policy (long trips only, 20% arm threshold, interface guard, 3s click spacing) and the forced Microbot.enableAutoRunOn=false was removed so inherited auto-run covers native quest walking. GameState exposes runEnergy/runEnabled; AutonomousPlanner applies a bounded extra travel cost at low energy. Pending: live verification of orb toggling and travel time.

0.9.2 implementation: Curriculum scores ready quests, quest supply prep, level gates, lowest/milestone skills and penalises ground loot outside emergency coins. BankSalePlanner chooses the highest-value protected bank stack; FundingTask withdraws it noted and sells at GE before public loot. BankMemory injected into Engine revenue and SupplyService funding. Pending: live verify bank sale and curriculum selection; then trained-policy prior from the labs.
0.9.3 implementation: QuestIntegration re-arms QuestScript TurnOn and recovers dialogue/re-selection; Engine delegate watchdog 30s/75s. ChainRunner counts busy continuous actions, requires fresh growth, partial-settles after verified progress. InventoryCleanup + BankTask.depositUnrelated clear leftovers on chain prepare, goal switch and full-inventory banking. Pending: live verification with the same scenarios.
0.9.4 implementation: AccountKnowledge one-time ledger + unlocks + readiness; CombatPolicy safe-melee exemption; Curriculum combat/skilling category boosts; Engine quest-failure suppression (2 fails -> 10 min no quests) and ACCOUNT_SCAN. Pending: live verify combat/skilling selection and one-time ledger; optional Stronghold route executor.
0.9.6 implementation: AiBridge (/chat,/monitor,/evolve,/evolve/feedback), AiChatWindow button, script monitor/action queue, GrowthLoop typed proposal+feedback, Hermes repos cloned. Pending: trusted code-fix pipeline (stage->backup->compile->test->apply->rollback) and live verification of growth proposals.
0.9.7 implementation: SupplyService no longer defers quest supplies before funding; FundingTask bank liquidation reachable with 3-minute budget. InventoryCleanup per-activity keep-set. Engine sessionUntil (60 min for skilling/combat) and chain SESSION_CONTINUE; seed chains 12 ACT. Server /fix/apply + dev.apply_fix backup/test/rollback/install. Pending: wire chat/growth agents to code fixes; live verify funding/inventory/session behavior.
