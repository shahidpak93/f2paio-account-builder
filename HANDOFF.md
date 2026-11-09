# Current release 0.14.0

Read [route comparison, startup and handoff](FIX-0.14.0.md) and [offline training results](training/osrs-v014/README.md). Restart Microbot to load 0.14.0. The sections below cover earlier releases.

# AI handoff: current release0.9.11

Project: C:/Users/shahi/Documents/Codex/2026-11-08/s/outputs/f2paio. User wants automatic F2P account progression, useful visible AI supervision and learning, general fixes rather than per-monster patches, bulk banking/selling and minimal manual choices. Money is subordinate. Preserve all newer changes: this workspace advanced through0.9.10 in another continuation; do not restore0.9.0 over it.

Read FIX-0.9.11.md first. Current code adds ActionSettlement and SetupCoordinator for bounded actual-effect setup across all combat profiles/gear slots. Do not restore the0.9.10 attempt-once-and-return-true logic: it bypassed confirmation. Essential setup failure defers and notifies AgentSupervisor; optional armour failure skips the item for this EquipmentPolicy instance. Accounts/targets have distinct gate keys and pauses shift waits. A widget mapping failure remains possible, now bounded and observable.

BankMemory commits only non-null native snapshots, throttles reads to1s and logs changed ID/quantity signatures. A native read failure does not establish known-empty stock. RunEnergy's existing policy stays enabled; potion use respects interfaces, notes and debounce. Energy potions are F2P, despite an earlier incorrect comment.

Retained architecture includes native quest integration/dynamic item requirements, curriculum, account knowledge/one-time ledger, chain runtime/evidence, contextual bandit, OutcomeLearning values/episodes, milestone focus, AgentSupervisor preflights/incidents, agent_lab team workflow and newer chat/monitor/evolution/fix tools. Consult actual source and tests for their current limits. Earlier role/feature descriptions are history, not proof of current behavior. Never print credentials or bundle agent_lab/state.

Build.ps1 detects client/JDK, emits Java11 bytecode and dist/build-info.json. Test.ps1 runs all current regression suites and loader checks. Full cached upstream compile uses work/Microbot. Install.ps1 installs the JAR and account-builder defaults. JAR/installed hashes and actual counts are in STATUS.json. Installed0.9.11; live gameplay unverified. The gateway was restarted for matching version metadata.

Pending: live weapon/style observations, complete supply acquisition graph, multi-stack funding plans and durable restart settlement; full original bible remains incomplete. Learning ranks supported actions from actual effects; it is not a general self-trained OSRS player. Do not conflate synthetic tests or model formatting scores with game success.

History is in HISTORY-HANDOFF.md, WORKLOG.md and prior FIX notes. Latest evidence: test-v0911.log, gradle-v0911.log, agent_lab/agent-tests.log. Report meaningful fixes with actual test evidence, and keep incomplete/live-unverified behavior explicit.

0.10 handoff: Curriculum.balance must run before AccountMilestones.focus. CombatStyleResolver reads live weapon metadata; do not restore fixed stance indices. GrowthLoop feedback is asynchronous/acknowledged and must not block Engine. ChainPlan.contentId prevents per-goal revision collisions; historical counts cannot bypass fresh retry proof. repair_jobs.py owns the serial Hermes worker, staged source snapshots, tests, stale-merge refusal and installation. RestartAfterRepair handles Python gateway reload; full fault-injection remains pending. Current reference corpus is310 facts/93 attributed pages; no new neural training. Runtime SDK is work/h-env and source cache is machine-specific per developer-config.json. Exclude hermes-home/state from packaging. The user authorized autonomous developer access; do not reintroduce permission prompts for routine project fixes.

Final0.10 evidence:155 account-builder checks,28 Python tests and upstream compile pass. Installed hash matches STATUS. Actual queued Hermes terminal call returned323 with one tool call. No autonomous source-fix/gameplay success is inferred from that probe. deployment_review blocks automatic repairs for missing/old runtime versions; manual /repair remains authorized. bump_java_version makes installed-but-not-reloaded Java repairs distinguishable. Python repair restart rollback is implemented but not live fault-injected.

## Offline OSRS training

The [training handoff](training/osrs-v014/README.md) contains source manifests, two saved LoRA adapters, a vision pilot and independent evaluations. The active plugin remains 0.13.0.
