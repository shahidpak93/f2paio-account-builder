# Work log

## 2026-11-08 — version 0.3.0, corrective autonomous controller

The user supplied a 0.2 run that was blocked by `Microbot.pauseAllScripts`, then repeatedly reported successful tree clicks without XP. Inspection found the fuzzy equipment query `isWearing("axe")`, which also matches a pickaxe. Replaced it with exact item-ID and usable-tier checks. Added feedback from game messages, armed confirmation before sending an action, and separated submission from observed animation/XP/products. Unconfirmed attempts time out and trigger replanning rather than indefinite clicking.

Start/Resume now explicitly clears the manual/global pause flags once. Later pauses and human input ownership remain respected. Added Skip, route cancellation and a ten-minute exclusion of the skipped goal.

Replaced the simple axe-only provisioner with a supply service using bank memory, unnoted item withdrawals, updated deficits, bounded coin withdrawals, GE owned-slot handling, shop fallback and funding. Funding uses surplus banked products or public drops, reserves current goal ingredients, and sells through GE/general store; public scavenging has a revenue goal to avoid accumulating banked items without cash. When inventory is full, supply recovery can bank unrelated items, while processing is allowed to transform a full inventory.

Integrated actual native Quest Helper / QuestScript execution, with eight quests, current-step missing tradable inspection, supply recovery and restoration of owned configuration. Added basic gear acquisition/equipping, carried energy-potion use, and native transport/teleport routing. Native APIs are reused instead of duplicating their UI loops.

Expanded the runtime registry to 48 definitions, including mining/fishing tiers, scavenging and highest-population eligible F2P PvP-world selection, plus cooking, chocolate grinding, amulet stringing and tanning. NPC Tanner references were adapted under the existing BSD notice. Online method research is recorded separately from active handlers.

The main window is an account progress dashboard. It includes skills, all quest states, carried/equipped/last-observed bank items, coin balance changes, events, Skip/Resume/Stop and debug export. Recent/final progress is saved. Keys are excluded from these records.

Validation covers the reported tool regression, noted/unusable tools, missing-item policies, action rejection/timeouts/confirmation, completed-quest filtering, Skip cooldown and dynamic F2P PvP world selection, plus earlier persistence/HTTP/DPAPI/lease/kill-monitor tests. A machine-readable registry is exported into `METHODS.json`. Full compilation and raw results are retained with the deliverable.

**Live status:** Microbot was not running during this update. No bank/GE trade, native quest completion, production batch, world hop or 20-minute unattended run was verified. The catalogue count is implemented definitions, not validated profitable methods. HANDOFF.md and GUIDE.md were rewritten for 0.3; do not follow the historical 0.1 instructions below.

---

## 2026-11-08 — initial implementation, version 0.1.0

### Inputs recovered

Read the user's full v3.1 attachment and saved it unchanged as `PROJECT-BIBLE-v3.1.md`. Searched the app's earlier chats, including Build OSRS Account Plugin, Build F2P Highlight Plugin and Microbot Overview. The older Account Builder chat explicitly ended without creating or compiling a plugin. No previous implementation was assumed from the bible's BUILT labels.

The user added that existing plugins/scripts may be downloaded, copied and substantially modified. Downloaded Microbot and Microbot-Hub, read their repository instructions, and reviewed relevant gathering, entity-query, threading, plugin-loading and quest sources. Source commits and reuse details are recorded in `SOURCES.md`.

### Environment findings

- Existing client: `C:\Users\shahi\.microbot\microbot-2.6.54.jar`.
- Existing JDK: Eclipse Adoptium 17.0.20.101.
- Existing local plugin directory: `C:\Users\shahi\.runelite\microbot-plugins`.
- Microbot root wrapper tried to download Gradle 8.8 and timed out before compilation.
- Found an existing cached Gradle 8.10.2 distribution and used it successfully.
- The actual client build targets Java 11. Replaced the attachment's Java 17 language assumptions with JDK 17 development plus `--release 11` output.

### Built

Created the standalone source project, build/install/stop scripts, dependency hashes, plugin JAR and isolated tests. Implemented plugin/script separation, a Swing window and toolbar entry, persisted settings and personas, Windows DPAPI key storage, native cache/entity interactions, a small method catalogue, product-only bank/drop, four quest delivery flows, basic combat, rotation and bandit selection, leases/quarantine, session pacing, watchdog, motion recording/replay, telemetry and four LLM backend adapters.

The custom scheduler/engine replaces separate copied script loops. Reused upstream constants/definitions and native runtime utilities are documented rather than claiming all upstream scripts were bundled unchanged.

### Problems caught and fixed

- The installed client lacks `getRs2InventoryCache()` and `isMembersWorld()`. Used supported inventory helpers and world-type flags.
- The installed Gson lacks static `JsonParser.parseString`. Used its supported parser API.
- Old/new NPC model classes differ between source and installed binary. Native cache model `.click(action)` avoids that overload mismatch.
- A randomized comparator would violate Java sorting's comparator contract. Sampled method scores once before sorting.
- Invalid endpoint schemes could produce a null URI host and a validator NPE. Guarded the host before loopback matching.
- Java inherited PowerShell 7's module path, breaking Windows PowerShell Security/DPAPI cmdlets. Removed `PSModulePath` only from the key helper's subprocess environment.
- Response streaming needed a real size bound and deadline. Added a bounded body subscriber and timed/cancelled HTTP future.
- A safety task on Microbot's ordinary script executor would stop polling while scripts are paused. Added a separate daemon safety monitor and an independent blocked-worker cancellation test.
- Native script shutdown can interrupt its calling worker before final persistence. Preserved/restored interrupt status around independent lease-release and telemetry-write attempts.
- Gathering rotation now clears current products first, avoiding accumulation of protected prior-method products.
- Corrected method anchors and retained upstream dialogue phrases before packaging.

### Verification evidence

- Baseline upstream `:client:compileJava`: **passed**, with 79 pre-existing warnings.
- Custom source copied into the same upstream client and `:client:compileJava`: **passed**, with no new compiler warnings in the incremental build output.
- Standalone `Build.ps1` against installed 2.6.54: **passed**, no compiler warnings.
- Isolated `Test.ps1`: **passed**. The check count includes a 10,000-sample distribution loop; it is not 10,000 separate gameplay scenarios.
- Tested config round-trip, stable persona persistence, URI rejection, watchdog escalation/STOP, overnight windows/session ceiling, bandit ranking, expiring leases/quarantine and actual child-JVM lease contention.
- Tested the independent emergency monitor while another executor was blocked.
- Tested motion transformation endpoints/curvature, local mock Ollama and OpenAI-compatible request/response paths, invalid recommendation rejection, request caps and oversized-body rejection.
- Tested Windows-user DPAPI save/load/forget with an isolated dummy secret, never a real user key.
- Native Microbot `LivePluginArtifact.read` accepted the final plugin packaging; manifest, private-class-only contents and Java 11 bytecode were checked.
- Opened a standalone Swing preview using a temporary profile. Inspected Activities and AI tabs using the computer-use skill, then closed the preview. Changed forms to align at the top after inspection.

Final command output is in `test-results.log` and `gradle-validation.log`. Binary dependency/output hashes are in `dist/build-info.json`.

### Installation

Installed `dist/F2pAioPlugin.jar` to `C:\Users\shahi\.runelite\microbot-plugins\F2pAioPlugin.jar` using `Install.ps1`. Compared SHA-256 hashes; the installed and delivered JARs match. The plugin has not been enabled in a live client. Rescan local plugins or restart Microbot, then enable F2P AIO manually.

### Not verified or finished

No OSRS account was logged in or controlled. No live gathering/banking/quest/combat run, 20-minute unattended session, real LLM provider call, actual fleet operation, bond transfer, PvP system or ML training was completed. Those remain in `HANDOFF.md`; they must not be presented as verified features.

### Continuation rule

After each meaningful change, update this file and the status/next steps in `HANDOFF.md`. Run the relevant tests; keep the guide truthful. Future agents should start with live verification and the missing engine-level asynchronous scenario harness, then proceed through the documented backlog.


## 0.3.1: fish pickup and normal-world selection

The user logs showed travel to Barbarian Village followed by no-target scans and no pickup submissions. The shared `isLootAble()` helper rejects every `OWNERSHIP_OTHER` item. Native Ground Items TAKEABLE logic instead allows those public items for normal accounts. `GroundLoot` now implements that account-aware rule, rejects private other-player drops, preserves self/group items, normalizes names and logs scan decisions. Both ordinary ground methods and supply funding use it. A submitted pickup is only confirmed after product quantity increases.

`WorldLootPolicy.preparePublic` now ranks current world-service populations for ordinary F2P scavenging as well as the existing PvP branch. It excludes members/PvP/restricted/full worlds for ordinary scavenging, records the selected population, waits for a hop to be observed and debounces settled selection for two minutes. Engine world-change timeout was extended to accommodate asynchronous hopping. Highest population is a heuristic for discarded items, not evidence of actual fishing activity; normal harvesting methods do not automatically prefer crowded worlds.

A separate accounting bug used `Rs2Inventory.count(995)` (matching slots) for coins. `GameState` now captures inventory once and sums actual coin quantities from that same snapshot. This corrects planner, funding and dashboard cash readings.

Validation: installed-runtime compilation, 13 additional loot/coin/world regression cases, existing 10,193 checks, native artifact loading and full upstream client compilation passed. The pre-fix live read-only probe at world 555 saw 14 cached and 14 raw ground items, including publicly visible OTHER-owned items; the cache/world-view registry was working, so no speculative cache patch was added. The client subsequently closed. End-to-end fish pickup, world hop and sale remain unverified with 0.3.1.

Next live check: restart the client, confirm 0.3.1 in the panel, Start auto and inspect `LOOT_SCAN`, `WORLD_HOP`, `WORLD_READY`, `ACTION_SUBMITTED` and `ACTION_CONFIRMED`. Confirm actual inventory change rather than inferring success from click submission. A correctly counted coin balance may cause the planner to choose a supplied quest instead of scavenging.

## 2026-11-08 — 0.6.0 learning, rotation and native integration repair

Removed regional scoring and the unused Region enum; expanded nearby target reuse beyond fixed NPC/loot anchors. Added whole-skill Skip cooldowns, two-session diversity and rotation before a full inventory. Persisted account-local contextual bandit history with separate XP/cash observations and valid fractional sampling; user Skip and planned rotation do not count as failures. Added schema checks, observed-effect probation, promotion and persistent quarantine for generated definitions. Retained existing client-thread price fix and earlier public-loot/world/coin repairs.

Replaced the separate price-based flipper with a native GE Flipper/Copilot bridge using the existing installed plugin and Finish lifecycle. Removed fabricated config-file discovery claims from the UI and current docs. Native trading owns its exposure settings and AIO suspends its own actions while delegated. Pacing changes now restore prior values without rewriting unrelated mouse/fatigue fields.

Validation: 10,193 existing checks, 13 loot/coin/world cases, 20 new learning/rotation/verifier cases, native artifact loading and full client compilation passed. Native installed Finish API signatures inspected; no live trading or gameplay outcome claimed. The client was closed during final validation. Guide, source provenance, handoff and machine-readable status refreshed.

## 2026-11-08 — 0.7.0 executable chains, evolutionary reuse and batching

Added ChainPlan/Draft compiler, replayable Runner, native Backend, account-local Library and Runtime; integrated ordinary gathering/fishing/drop/processing into actual chain execution. Added verified promotion, failure quarantine, bounded mutation/exploration and parent fallback. Dashboard now exposes plan and evidence counts. Shared supply/sale services keep pending ownership through chain changes; supply offers settle before new acquisition. Added explicit quest-completion exit, pause-aware deadlines and world-transition grace.

Fixed SELL 1 root cause by grouping all unstacked slots for the same item ID; kept GE open between batches. Added reachable loot selection, item/tile backoff and partial-batch settlement. Independent Anonymous rendering errors remain outside this plugin.

Real configured Gemini check returned HTTP 404. Verified installed local Ollama model with valid JSON. The initial verbose AI chain failed schema checks; compact capability compilation fixed the drafting contract. A later actual provider-fallback request produced a validated shrimp/fish→cook→bank proposal, saved as an unproven example. No credentials exposed, no weights trained.

Final regression: 10,193 existing checks +13 loot/coin/world +20 earlier learning +97 chain/replay/evolution/batch checks; native loader/Java 11 packaging and full client compile pass. Client closed: no v0.7 gameplay, bulk sale or Copilot success claim. Current implementation gaps explicitly recorded in FEATURES.md.


## 0.8.0 — account builder and local trained agents

Added account-first policies, 200 researched tips/40 source revisions, 64 methods/20 native quests, noted bulk funding, gear locks/upgrades, two installed local models, actual CPU LoRA training and four-role agent service. Thirteen agent tests, 101 new account-builder checks and full upstream compilation pass. Free-generation mistakes remain documented; rule verification overrides model advice. Live 0.8 gameplay pending. See AI-SETUP and training/model reports for exact training evidence.

Final integration caught and rejected an invalid neural recommendation. Added deterministic handling for a sole registered eligible choice, tested Java-to-gateway successfully, and fixed direct-Ollama fallback when the local gateway fails. Thirteen Python tests and Java/upstream checks passed after the change. Sole-choice integration success is controller behavior, not evidence of new model intelligence.


# 0.8.1 purchase-loop fix

The supplied logs show repeated BUY 1 Eye of newt, GE_COLLECT result=true and immediate missing-supply selection, from09:47 through09:58. Inventory occupied slots stayed roughly constant; that does not reveal individual item quantity/form. The player then ran low on coins and walked to the general store to sell shrimp. This is a genuine acquisition loop, not the earlier manual pause.

Root controller defect: TradeService treated the native collection return value as completed acquisition and reset the retry counter, without observing the bought inventory delta. The logs do not establish why the expected usable item was missing: note form, metadata/recognition or collection behavior need a fresh diagnostic if it recurs.

0.8.1 adds an eight-second inventory-delta receipt check after collection. Unverified purchase blocks that item for ten minutes on this TradeService instance and emits GE_RECEIPT_FAILED; no immediate repurchase. Verified gains emit GE_RECEIPT_VERIFIED. Owned noted supplies are deposited and withdrawn as items before buying again. Quest ingredient recognition uses numeric unnoted item IDs rather than relying on display names.

Tests:106 account-builder checks (five new receipt/identity cases), existing10193 checks plus13 loot/coin/world,20 learning and97chain checks pass. Native loader and full upstream client compilation pass. Patch installed; restart Microbot and confirm0.8.1. In-game fix still needs live confirmation. Existing GE offers are preserved; reconcile them before starting another session.

Separate attachment exception: com.flippingcopilot.ui.NpcHighlightOverlay.render raised a NullPointerException through ModelOutlineRenderer. That is the Copilot NPC highlight rendering path, not an AIO stack trace; it has not been patched here.


# 0.8.2 account-builder funding correction

User logs showed Skip from Prayer followed by String gold amulets requiring10 Gold amulet(u) and10 wool, then a1613GP deficit and prolonged loot travel. This was not meaningful account progression. The planner counted missing requirement types rather than affordability. FundingTask sold as soon as any saleable item existed, causing trips for one or two items.

Changes: BuilderBudget filters unaffordable methods using carried coins, observed bank coins/materials and usable noted stock before selection and AI suggestions. Basic axe/pickaxe get a bounded150GP bootstrap allowance. During preparation, account-builder mode defers other unfunded supplies instead of invoking open-ended looting. Tool funding is limited to two minutes. Other affordable goals remain selectable; no material stock/GP is invented. A deferred quest can be revisited when materials/coins are available. This patch does not implement new gathering executors for every missing quest ingredient.

Funding sale batches now wait for estimated value at least max(250GP, current deficit),24 occupied inventory slots, or60 seconds with no matching nearby drops. Existing owned sale offers retain priority. A dry spot can therefore still leave with a small batch after the bounded wait; it no longer leaves immediately solely because one shrimp was picked up.

112 account-builder checks,10193 original checks,13 loot/coin/world,20learning and97chain checks pass. Native loader and upstream client compilation pass. Version0.8.2 installed, hashes match. Live behavior pending: restart Microbot, confirm0.8.2, observe affordable goal and real XP/quest progress. The complete account builder remains unfinished; this fixes the observed funding priorities rather than claiming full account progression proven.


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

## 2026-11-08 — version 0.9.1, run energy

The user asked why the bot never used the run button. Two causes: `F2pAioScript.run()` forced `Microbot.enableAutoRunOn = false`, which removed Microbot's inherited `Script.run()` auto-run and the walker's requestAutoRun(); and the plugin's walk helper only drank a carried (members-only) energy potion, so it never clicked the orb and F2P never had a potion to drink. Every trip was walked.

`RunEnergy` now owns the decision: enable on trips of at least 10 tiles above 20% energy, no click on short repositioning, hold while a dialogue/bank/shop/GE interface owns input, leave run on once enabled, and space clicks by 3 seconds. The forced `enableAutoRunOn = false` is removed so Microbot's own guarded auto-run stays available (this covers native quest-script walking during DELEGATE) while the plugin's policy handles its own walks. GameState snapshots energy and orb state; heartbeat logs run percentage; travel status shows it; `RUN_STATE`/`RUN_ENABLE`/`RUN_HOLD` explain decisions in the debug log. The planner charges a slightly larger bounded travel penalty when energy is absent. 16 offline run-energy cases were added; all prior suites, the upstream Microbot compile, packaging and install passed. Live toggling is still unverified.

## 2026-11-08 — version 0.9.2, curriculum and bank-funded GP

Baseline from recorded sessions showed ~806 XP/h average and ground-collection goals winning early sessions. Added `Curriculum`: ready quest +140, quest supply-prep +15, level-gated quest -20, lowest trainable skill +45, next-milestone skill +20, ground loot -70 outside emergency coins. Milestones 10/20/40/60/80/99; aim shown in plan view and logged as CURRICULUM. Added `BankSalePlanner` and wired it into `FundingTask`: when a goal is unfunded it withdraws the highest-value banked product stack noted and sells it at the GE before public-loot scavenging; coins, tools, worn gear, food, reserved quest items and production inputs are protected; failures back off for five minutes. `FundingTask` now receives `BankMemory` from Engine and SupplyService. 14 new offline cases; all prior suites, upstream compile, packaging and install pass. Live verification pending.

## 2026-11-08 — version 0.9.3, runtime recovery fixes

Live log showed three interacting failures. Quest Helper: QuestScript writes TurnOn=false when it stops; integration set it after start and then returned early, so the helper idled for two minutes until the supervisor deferred the quest. Now TurnOn is set before start, re-armed up to 3 times, a recovery clicks dialogue/re-selects the quest, and the engine watchdogs 30s recovery / 75s defer (3-minute cooldown) while excluding Microbot pauses. Chains: ACT only fired when idle, so fishing/mining never re-entered VERIFY and timed out after real catches; now busy-with-prior-action steps into VERIFY, effects require new growth since the last verified snapshot, and a quiet window after verified progress settles as CHAIN_PARTIAL instead of failing (zero-effect chains still fail). Inventory: only current-goal products were deposited; added InventoryCleanup keep-set, BankTask.depositUnrelated, chain-prepare junk banking at near-full inventory, engine cleanup on goal switch and in BANK when full. 10 new offline cases; all prior suites, upstream compile, packaging and install pass. Live verification pending.

## 2026-11-08 — version 0.9.4, account knowledge and category balance

User reported only quest/money goals, no combat or skilling, no awareness of one-time rewards such as the Stronghold of Security 10k. Causes: ready-quest +140 dominated; combat required weapon/armour/food so the affordable filter removed it; no one-time or unlock knowledge. Added AccountKnowledge with a persisted per-account one-time ledger (Stronghold 10k + boots known, claimed once, reward-item ownership counts as claimed), quest unlock map and combat/skilling readiness, plus ACCOUNT_SCAN logging and plan view. Added CombatPolicy: safe melee chickens/cows need no food or gear; ranged/magic keep weapon requirements. Curriculum now boosts combat +60 when ignored for 10 minutes and skilling +15; after two quest failures quests are suppressed for 10 minutes. Farmable methods stay repeatable; quests and registered rewards do not. Stronghold execution is intentionally not implemented (registry only, per user instruction). 16 new offline cases; all prior suites, upstream compile, packaging and install pass.

## 2026-11-08 — version 0.9.6, AI chat, active monitor and growth loop

Added an AI chat button on the dashboard backed by /chat: sends a live snapshot (status, plan, goal, phase, position, coins, levels, quests, items, recent debug events) and returns a diagnosis plus one whitelisted action that the controller executes (whitelist enforced by AiBridgeTests). Added active monitoring: every 120s the engine posts the snapshot to /monitor; deterministic rules return severity/action and whitelisted actions are auto-queued (AI_MONITOR / AI_ACTION_AUTO). Added GrowthLoop: every 30 minutes engine sends chain/library details (probation, quarantine reasons), learned skills and levels to /evolve; the agent returns one typed skill or chain; Java validates with SkillProbation.valid / ChainPlan.valid, saves it on probation, runs it, then reports verified outcomes via /evolve/feedback so agents evolve from evidence. Cloned Hermes agent repos into labs as an external runtime reference. Gateway endpoints are token-authenticated and audited; no arbitrary shell or hot code replacement. 7 new whitelist cases; all suites and upstream compile pass.

## 2026-11-08 — version 0.9.7, bank funding, per-activity gear, long sessions, auto-fix

Fixed quest/supply funding: the account-builder defer ran before FundingTask, so a bank with 30 stacks never funded a 2,985 gp quest requirement. Removed the early defer; funding now liquidates banked loot (withdraw noted, GE sell) with a 3-minute budget. Inventory keep-set is per activity: only the current method's tools/inputs, food, coins and worn gear are kept; other tools are banked and re-acquired when that activity is selected. Chain seed ACT count is 12 and completed skilling/combat chains continue the same goal until a session budget (min 60 min) instead of abandoning every few minutes. Added auto-fix pipeline: POST /fix/apply and developer tool dev.apply_fix write a backup, apply a full Java replacement to src/, run the full offline suite, roll back automatically on failure, then optionally install the jar; fully audited, no approval step. All offline suites, packaging and upstream compile pass.

## 2026-11-08 — version 0.9.10, universal setup/quest fixes

Live log showed chickens stuck in an endless PREPARATIONS_OK -> TRAIN_STYLE -> set attack style loop: CombatTraining returned false until the stance varp updated, blocking PREPARE for two minutes then deferring combat. Fixed generically: every combat style (melee, ranged, magic) is attempted once and the engine proceeds, with an 8s debounce; EquipmentPolicy uses the same attempt-once pattern for every slot so gear cannot block PREPARE. QuestIntegration now reads the QuestHelper quest-level item requirements for ANY quest instead of a hand-maintained list (the Goblin dye map remains only a fallback); tradeable unmet items become supply goals. Quest watchdog no longer resets its idle clock on re-arm, so the 75s quest defer fires before the generic 2-minute supervisor; supervisor incident cooldown for quests is 3 minutes. AI monitor no longer auto-REPLANs while the planner is selecting, and only auto-banks leftovers when the inventory is at least 20 slots. Carried coins are banked unless the current activity needs them (supply withdrawal fetches them when buying). All offline suites and upstream compile pass.


# 0.9.11 shared policy fixes

Reviewed the attachment against the actual workspace, which already contained changes through0.9.10. Preserved run-energy handling, native quest item discovery, curriculum, account knowledge, AI chat/monitor/evolution, existing outcome learning and latest recovery changes.

## Shared fixes

ActionSettlement replaces attempt-and-assume-success in every equipment slot and melee/ranged/magic setup. It attempts once, waits five seconds for the real state, retries at most three times and then reports failure. READY requires observation, not a click return. Account/target changes get a separate attempt budget; manual pauses do not consume the wait. This avoids both endless PREPARE and fighting with an unconfirmed style.

SetupCoordinator applies consistent outcome handling across all combat profiles. Failed essential weapon/ammo/style setup defers the goal and notifies the supervisor. Failed optional armour upgrades are skipped for the session while retaining worn gear. Setup waits while a blocking dialogue/bank/shop/GE interface owns input. No monster-specific name or coordinate is used by these rules. Existing weapon widget mappings still require live verification; incorrect mappings now produce a bounded diagnostic rather than silently counting as success.

BankMemory commits only successful snapshots. A failed read cannot turn unknown bank stock into a known-empty bank or erase previously observed items. Reads are limited to once per second, and BANK_SCAN logs only changes in item IDs/quantities. This applies to every bank and activity.

Run-energy changes from0.9.10 are preserved. Carried energy potion use is debounced, excludes notes and respects interface ownership. Corrected the stale statement that energy potions are members-only: they are available in F2P. Source: https://oldschool.runescape.wiki/w/Energy_potion

## Validation

136 account-builder/setup/bank checks,16 run-energy,14 bank/curriculum,10 inventory/chain recovery,16 account-knowledge and7 AI-whitelist cases pass, alongside the existing10193 checks,13 loot/coin/world,20 learning and97 chain checks. Fifteen Python agent tests pass. Native artifact loading and full upstream Microbot compilation pass. Installed JAR hash is checked against the built artifact in STATUS.json.

Restart Microbot and confirm0.9.11. No live0.9.11 gameplay success is claimed. These are shared policy fixes, not proof that every quest, combat weapon, or original-bible feature works. Full automatic restart settlement, complete item-source planning and live regression coverage remain unfinished.


# F2P AIO 0.10.0

## Research applied

Added110 short attributed facts to the existing200:310 facts now reference93 distinct OSRS Wiki pages. The full acquisition read98 documents covering96 distinct wiki pages,739,416 cached characters. Two attempted pages were unavailable and irrelevant/disambiguation material was excluded from rules. See knowledge/PLAYER-KNOWLEDGE.md, PLAYER-POLICIES.json, sources.json, research-manifest.json and SOURCES-DEEP.md. Raw articles remain in work, outside the package. This is public guide/mechanics knowledge, not human gameplay recordings or a new neural fine-tune.

References cover beginner/progression guides, combat styles, resource chains, transportation, account restrictions, reward eligibility and public AIO frameworks. Adapted ideas include live-condition task branches, explicit prerequisites, persistent milestones, effect-based checks, reusable skills and feedback-driven revision. No closed-source scripts were copied.

## Concrete account-building fixes

- Combat fairness now runs before quest-only milestone filtering. Previously the filter removed combat candidates before their priority bonus could matter. Due combat and skilling can now receive turns even with ready quests.
- Combat styles resolve from live weapon enum/struct metadata. Strength/Defence positions are not assumed identical for fists, daggers and other weapons. The Combat tab is opened before stance clicks. Existing bounded observed-effect verification remains.
- New method unlocks request re-evaluation after the current verified chain, or between combat segments. A higher unlocked tier is reconsidered, not automatically treated as better XP.
- Basic self-cooked foods are recognised. Injured accounts without usable food do not immediately select another combat task; emergency retreat targets the nearest bank rather than one hard-coded distant city.
- One-time Stronghold evidence reads native reward varbits and carried/equipped/banked boots, preserving account-local records. Farmable drops remain repeatable. No Stronghold reward route was added or requested.
- Corrected Rune Mysteries' unlock label to essence-mine access and removed the incorrect X Marks-to-Corsair route label. Ironman state now prevents assuming missing supplies can be bought through the GE. Full Ironman self-supply routing remains incomplete.

## Chat and developer access

Open AI chat in the plugin panel. Chat reads a bounded recent context plus live state, inventory and dialogue when available, retrieves relevant facts and retains account-separated history. Use Auto-fix with Hermes or type /repair followed by the issue. Repair jobs shows actual status, summary and errors.

Hermes is configured with an OSRS-specific home, skills, local models and actual terminal/file/internet access under the Windows user. This is not limited to runtime action suggestions. One developer job runs at a time; repeated automatic repairs have a15-minute cooldown. The regular planner remains the2B model; the developer uses the existing4B model with a2B fallback. Current Hermes requires64K context. Dependency/path problems were repaired using a shorter local SDK environment; details are in HERMES-SETUP.md.

Developer jobs work in a separate source copy, run host-owned Java checks and Python checks when appropriate, reject stale merges, and install tested changes. Failed direct source fixes restore source/artifacts and preserve a concurrent edit. Java JAR changes require Microbot reload. Tested Python changes request a gateway restart with a source rollback helper if startup fails. Runtime action preconditions remain active; a developer's terminal access does not make missing gear or false success acceptable.

The monitor polls every30 seconds and recurring technical failures can enqueue repair jobs. Incident supervision remains separate and defers the failing task immediately. Combat stalls are no longer mislabelled as quest restarts. Full automatic code-repair quality and gateway rollback recovery are not established by the connectivity probe.

## Learning loop repaired

Growth feedback no longer blocks the game loop on a network request. Results are sent asynchronously, retried if unacknowledged, and partial chain successes/failures are reported as well as promotion/quarantine. Evolution reads that previous feedback and prioritises failed/probation evidence. Proposed revisions use content-derived IDs instead of colliding on one fixed ID per goal. Quarantine retries keep historical counts separately and require fresh proof. No chain is marked proven merely because an AI proposed it.

Existing outcome-value learning and the contextual bandit continue learning from actual XP/quest/effect evidence. New declarative skills can enter live probation; new Java executors need a rebuilt/reloaded plugin. This does not train a general OSRS player from scratch.

## Verification

155 account-builder checks plus the existing suites pass:10,193 original checks,13 loot/coin/world,20 learning,97 chain,16 run-energy,14 curriculum,10 inventory recovery,16 account knowledge and7 runtime-action cases.23 Python agent tests pass. Native artifact loading and full upstream compilation pass.

Hermes directly called the terminal and observed49 in an initial probe. The authenticated developer queue then called the terminal and observed323, recording one tool call and no source changes. These prove connectivity, not a successful autonomous gameplay repair. Earlier failed setup/narration probes were retained, not counted as success. Live0.10 gameplay, complete account building and all original-bible features remain unverified.

Final0.10 validation:27 Python tests,155 account-builder cases, original suites, native loader and upstream compile pass. Hermes queue executed a real terminal tool; chat grounding rejects unsupported answers and read-only chat returns NONE. Installed SHA matches STATUS. Live gameplay remains unverified.

Final build installed and hash checked.28 Python tests pass; native/upstream Java checks pass. Latest chat probe is grounded and requests no action. Developer queue terminal proof remains read-only; actual self-fix/gameplay quality is not declared proven.

## 0.11.0 progression

Added shared route ranking, earlier reconsideration, combat tiers for all three styles, ranked gear purchase checks and verified recipe dependencies. Installed the matching JAR and restarted the gateway. The offline, native loader and upstream build checks passed. Live transition and gear tests are pending. See [release handoff](FIX-0.11.0.md).

## 0.12.0

Installed hierarchical prerequisite planning and online goal Q-learning. Added repeatable Smithing and Runecraft entries. Corrected gateway output shape and removed unnecessary model calls for research and verification. Added a seeded comparison of experimental learning methods. See [release handoff](FIX-0.12.0.md). Live gameplay verification is pending.

## 0.13.0

Cached 18 current OSRS Wiki revisions and downloaded 818 F2P item records. Registered 34 more methods and connected a held-out tested human demonstration prior to goal scoring. Installed the verified JAR and exercised the live local data tools. Live gameplay validation is pending. See [release handoff](FIX-0.13.0.md).

## OSRS data and training experiment

Downloaded 48 revision-pinned references and 7 videos. Trained two LoRA adapters and a bank-visibility classifier. The balanced adapter scored 6/12 on independent decisions. The vision pilot struggled after bank closure. See [training handoff](training/osrs-v014/README.md).

## 0.14.0

Added account-snapshot route comparison and one alternate production branch per failed supply attempt. Reviewed three public OSRS projects and kept references pinned in the handoff. Installed matching JAR and gateway builds. Replay and native checks passed. Live gameplay verification is pending.
