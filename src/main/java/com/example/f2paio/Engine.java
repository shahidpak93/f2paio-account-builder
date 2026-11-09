package com.example.f2paio;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
public final class Engine
{
    public enum Phase { SELECT, PREPARE, TRAVEL, WORK, BANK, REVENUE, DELEGATE, FLIP, COMPLETE }
    private final F2pAioConfig config;
    private final String accountHash;
    private final Metrics metrics;
    private final Watchdog watchdog;
    private final SharedStore shared;
    private final AgentBus agent;
    private final F2pAioActions actions;
    private final HumanInput input;
    private final Random random;
    private final BankTask bank = new BankTask();
    private final BankMemory memory = new BankMemory();
    private final SupplyService supplies = new SupplyService(memory);
    private final GearUpgrades upgrades=new GearUpgrades(memory,supplies);
    private final Requirements requirements = new Requirements();
    private final GoalGraph goals = new GoalGraph();
    private final Quests quests = new Quests();
    private final QuestIntegration questHelper = new QuestIntegration();
    private final AutonomousPlanner planner = new AutonomousPlanner();
    private final EquipmentPolicy equipment = new EquipmentPolicy();
    private final SetupCoordinator setup=new SetupCoordinator();
    private final WorldLootPolicy worlds = new WorldLootPolicy();
    private final ActionMonitor confirmation = new ActionMonitor();
    private final ProductionActions production = new ProductionActions();
    private final FundingTask revenue = new FundingTask(memory);
    private final Bandit bandit;
    private final OutcomeLearning outcomes;
    private final GoalLearning goalLearning;
    private final AccountMilestones milestones;
    private final Curriculum curriculum = new Curriculum();
    private final AgentSupervisor supervisor=new AgentSupervisor();
    private GameState observed;
    private long pausedAt;
    private long revenueTarget;
    private final TaskRotation rotation = new TaskRotation();
    private final AtomicBoolean skip = new AtomicBoolean();
    private final FlipTask flipTask = new FlipTask();
    private final String owner = java.util.UUID.randomUUID().toString();
    private Method current;
    private long nextProgressionCheck;
    private long initialBankDeadline;
    private boolean unlockPending;
    private java.util.List<String> availableSnapshot=java.util.List.of();
    private volatile Phase phase = Phase.SELECT;
    private long selectedAt;
    private long nextAt;
    private long leaseAt;
    private final RecoverySupervisor recovery = new RecoverySupervisor();
    private final DiscoveryCoordinator discovery = new DiscoveryCoordinator();
    private final ChainRuntime chains;
    private long emptySince;
    private int failures;
    private int worldAttempts;
    private long lastXp;
    private long lastCoins;
    private CompletableFuture<String> suggestion;
    private long suggestionDeadline;
    private List<String> suggestedChoices=List.of();
    private volatile String status = "Reading account and selecting a goal";
    private volatile boolean closed;
    private boolean logoutRequested;
    private String previous = "";
    private boolean cleanupPending;
    private String delegateSig = "";
    private long delegateSince;
    private int delegateRecoveries;
    private int questFailures;
    private long questSuppressUntil;
    private long sessionUntil;
    private final AccountKnowledge knowledge;
    private final GrowthLoop growth;
    private boolean knowledgeLogged;
    public Engine(F2pAioConfig config, Persona persona, Random random, Metrics metrics,
        Watchdog watchdog, SharedStore shared, AgentBus agent, HumanInput input)
    {
        this.config = config; this.accountHash=persona.id; this.metrics = metrics; this.watchdog = watchdog;
        OfferJournal.account(persona.id);
        supplies.accountBuilder=config.accountBuilder && !config.taskMode.equals("MONEY");
        this.shared = shared; this.agent = agent; this.actions = new F2pAioActions(input);
        this.random = random; this.input = input;
        this.chains = new ChainRuntime(persona.id,random);
        this.bandit = new Bandit(FilesStore.ROOT.resolve("learning").resolve(persona.id + "-bandit.json"));
        outcomes=new OutcomeLearning(FilesStore.ROOT.resolve("learning").resolve(persona.id+"-outcomes.json"));
        goalLearning=new GoalLearning(FilesStore.ROOT.resolve("learning").resolve(persona.id+"-goal-q.json"));
        milestones=new AccountMilestones(FilesStore.ROOT.resolve("learning").resolve(persona.id+"-milestone.json"));
        knowledge=new AccountKnowledge(FilesStore.ROOT.resolve("learning").resolve(persona.id+"-unlocks.json"));
        growth=new GrowthLoop(FilesStore.ROOT.resolve("learning").resolve(persona.id+"-growth.json"));
    }
    public String status() { return status; }
    public String planView() { return curriculum.aim()+" | "+knowledge.view()+" | "+milestones.view()+" | "+chains.view(); }
    public String learning() { return chains.library().summary()+" | "+outcomes.summary()+" | "+goalLearning.summary()+" | "+supervisor.status(); }
    /** Compact JSON snapshot for the chat/monitor/growth agents. */
    public String aiContext()
    {
        var map = new java.util.LinkedHashMap<String,Object>();
        map.put("pluginVersion",Tuning.VERSION);map.put("accountId",accountHash);map.put("status", status); map.put("plan", planView()); map.put("learning", learning());
        map.put("phase", phase.name()); map.put("goal", current == null ? null : current.id);
        map.put("goalLabel", current == null ? null : current.label);map.put("goalKind",current==null?null:current.kind.name());
        map.put("availableAtSelection",availableSnapshot);
        GameState s = observed;
        if (s != null)
        {
            map.put("position", s.location == null ? null : s.location.toString());
            map.put("coins", s.coins); map.put("inventoryCount", s.inventory);
            map.put("busy", s.busy); map.put("combat", s.combat); map.put("health", s.health);
            map.put("levels", s.levels);map.put("weaponType",s.weaponType);map.put("attackStyle",s.attackStyle);map.put("rewardFlags",s.rewardFlags);map.put("chainDetails",chains.library().details().stream().limit(24).collect(java.util.stream.Collectors.toList()));
            java.util.List<String> finished = new java.util.ArrayList<>();
            s.quests.forEach((quest, state) -> { if ("FINISHED".equals(state)) finished.add(quest); });
            map.put("questsFinished", finished);
            map.put("items", s.items); map.put("equipment", s.equipment);
        }
        var recent=DebugLog.recent();map.put("recent",recent.subList(Math.max(0,recent.size()-40),recent.size()));
        return FilesStore.JSON.toJson(map);
    }
    public void requestCleanup() { cleanupPending = true; DebugLog.event("AI_ACTION", "requested leftover bank cleanup"); }
    public void requestQuestRecovery()
    {
        if (current != null && current.kind == Method.Kind.QUEST) questHelper.recover(observed);
        DebugLog.event("AI_ACTION", "requested quest helper recovery");
    }
    public boolean complete() { return phase == Phase.COMPLETE; }
    public boolean logoutRequested() { return logoutRequested; }
    public java.util.List<ResourceItem> bankItems() { return memory.items(); }
    public void feedback(String message) { confirmation.feedback(message); chains.feedback(message); }
    public void pause(long now) { chains.pause(now); supervisor.pause(now); equipment.pause(now); CombatTraining.pause(now); if(pausedAt==0) pausedAt=now; }
    public void requestSkip()
    {
        skip.set(true);
        Rs2Walker.setTarget(null, "f2paio:user-skip");
        DebugLog.event("SKIP_REQUEST", "Cancel current route; select a different goal at the next cycle");
    }
    public void step(GameState state, long now) throws Exception
    {
        if (closed || Thread.currentThread().isInterrupted()) return;
        observed=state;
        knowledge.refresh(state,memory.items()); knowledge.updateView(state);
        if(config.llmEnabled) growth.tick(now, state, chains.library());
        if(!knowledgeLogged){ knowledgeLogged=true; DebugLog.event("ACCOUNT_SCAN", knowledge.view()
            + " | one-time available=" + knowledge.available(state).size()); }
        if(pausedAt!=0) {long delay=Math.max(0,now-pausedAt);outcomes.pause(delay);goalLearning.pause(delay);pausedAt=0;}
        metrics.observe(state); memory.observe(); watchdog.observe(state.progressSignature(), now);
        RunEnergy.maintain(state, now);
        if (skip.getAndSet(false)) {
            if (phase == Phase.FLIP) { flipTask.cancel(); status="Skip: waiting for native flipper Finish"; return; }
            if (current != null) rotation.excludeSkill(current, now, 10 * Tuning.MINUTE);
            abandon("Skipped by user (skill family cooldown)", now, 10 * Tuning.MINUTE);
        }
        if (state.health <= 0) { goalLearning.finish(state,now,false); status = "Death detected; recovery required"; phase = Phase.COMPLETE; return; }
        if (watchdog.killed()) { status = "Kill switch"; phase = Phase.COMPLETE; return; }
        if(supervisor.step(current,state,now,phase.name(),status,memory.items(),config.llmEnabled)) {
            Rs2Walker.setTarget(null,"f2paio:supervisor-incident");
            long cooldown = current != null && current.kind == Method.Kind.QUEST ? 3 * Tuning.MINUTE : 10 * Tuning.MINUTE;
            abandon("Supervisor incident; offending goal deferred",now,cooldown);return;
        }
        if (current != null && ProgressionPolicy.trains(current) && !config.taskMode.equals("MONEY")
            && phase != Phase.FLIP
            && now >= nextProgressionCheck && !unlockPending) {
            nextProgressionCheck = now + 30_000;
            unlockPending = milestones.unlocked(state)
                || ProgressionPolicy.hasBetter(current, eligible(state, now),method -> outcomes.rate(method,state));
            if (unlockPending) DebugLog.event("UNLOCK_REPLAN", current.id + ": training upgrade or quest unlock reached");
        }
        Watchdog.Level level = watchdog.level(now);
        if (level == Watchdog.Level.LOGOUT)
        { logoutRequested = true; phase = Phase.COMPLETE; status = "Watchdog logout"; return; }
        if (level == Watchdog.Level.ABANDON) { abandon("No observed progress", now, 5 * Tuning.MINUTE); watchdog.pause(now); }
        if (state.combat && state.health <= config.eatAt)
        {
            var food = state.items.stream().filter(i -> !i.noted && Requirements.FOOD.contains(i.name))
                .findFirst().orElse(null);
            if (food != null) Rs2Inventory.interact(food.id, "Eat");
            else {
                status="No food during combat; retreat toward nearest bank";
                var refuge=Rs2Bank.getNearestBank();
                actions.walk(refuge!=null?refuge.getWorldPoint():new net.runelite.api.coords.WorldPoint(state.location.getX()+8,state.location.getY(),state.location.getPlane()));
            }
            return;
        }
        if (current != null && current.kind == Method.Kind.COMBAT && !state.combat)
        {
            var food = state.items.stream().filter(i -> !i.noted && Requirements.FOOD.contains(i.name))
                .findFirst().orElse(null);
            if (food != null && state.health <= 65) Rs2Inventory.interact(food.id, "Eat");
        }
        if (now < nextAt) return;
        if (current == null || phase == Phase.SELECT)
        {
            choose(state, now);
            if (current == null) return;
        }
        String check=supervisor.preflight();
        if(check.equals("WAIT")) {status="Waiting for verifier preflight";return;}
        if(check.equals("REJECT")) {abandon("Verifier rejected selected goal",now,5*Tuning.MINUTE);return;}
        if (now >= leaseAt)
        {
            if (!shared.lease(current.id, owner, 2 * Tuning.MINUTE, now))
            { abandon("Activity leased by another process", now, Tuning.MINUTE); return; }
            leaseAt = now + Tuning.MINUTE;
        }
        if (phase != Phase.FLIP && revenue.pendingSale()) {
            revenue.step(state,actions,Long.MAX_VALUE,java.util.Set.of());
            status="Settle previous owned sale: "+revenue.status(); nextAt=now+1_200; return;
        }
        if (phase == Phase.FLIP)
        {
            FlipTask.Result fr = flipTask.step(state, actions, config.flipMinCoinsAfter);
            status = "GE Flip: " + flipTask.status();
            if (fr == FlipTask.Result.DONE || fr == FlipTask.Result.FAILED)
            {
                flipTask.cancel();
                DebugLog.event("FLIP_END", "Flip session complete; resuming skilling");
                phase = Phase.SELECT;
                if (sessionUntil > 0 && now >= sessionUntil)
                    abandon("Post-flip activity rotation", now, 2 * Tuning.MINUTE);
            }
            nextAt = now + Tuning.ACTION_SETTLE_MS;
            return;
        }
        if(phase==Phase.PREPARE && !state.busy && !state.combat
            && !upgrades.step(current,state,actions,config,now)) {
            status=upgrades.status(); nextAt=now+1200; return;
        }
        if (ChainPlan.supported(current)) {
            ChainRunner.Result result=chains.step(current,state,now,actions,config,supplies,revenue,memory,
                rotation.excluded(now));
            status=chains.status(); phase=Phase.WORK;
            if(result!=ChainRunner.Result.RUNNING) {
                boolean ok=result==ChainRunner.Result.DONE;
                bandit.reward(current.id,ok,Math.max(0,state.xp-lastXp),0);
                if(ok && current.kind!=Method.Kind.QUEST && now<sessionUntil && !unlockPending) {
                    chains.reset(); supplies.reset(); phase=Phase.PREPARE; status="Continue session: "+current.label;
                    DebugLog.event("SESSION_CONTINUE", current.id+" until session budget"); nextAt=now+1500; return;
                }
                if(ok && !unlockPending && config.enableFlipping && state.coins>=config.flipMinBudget && !state.members) {
                    chains.reset(); flipTask.reset(); phase=Phase.FLIP;
                } else abandon(ok?"Completed verified chain":"Chain failed: "+chains.failure(),now,ok?Tuning.MINUTE:5*Tuning.MINUTE);
            }
            nextAt=now+600; return;
        }
        if (current.kind == Method.Kind.QUEST && phase == Phase.DELEGATE)
        {
            if("FINISHED".equals(state.quests.get(current.label))) {
                bandit.reward(current.id,true); questFailures=0; abandon("Quest completed",now,Tuning.MINUTE); return;
            }
            var missing = questHelper.missingTradeables(state);
            if (missing.isEmpty()) missing = new Requirements().questNeeds(current.id, state);
            if (!missing.isEmpty())
            {
                questHelper.pauseForSupplies();
                supplies.step(current, state, actions, missing); status = "Quest supply recovery: " + supplies.status();
                if (!supplies.failure().isEmpty()) abandon(supplies.failure(), now, 5 * Tuning.MINUTE);
                nextAt = now + 1_200; return;
            }
            questHelper.resumeOwned();
            status = "Quest Helper: " + current.label + " (native QuestScript running)";
            boolean paused = net.runelite.client.plugins.microbot.ScriptPauseController.isPaused();
            String sig = state.location + ":" + state.inventory + ":" + state.questPoints + ":"
                + state.quests.getOrDefault(current.label, "") + ":" + state.busy;
            if (paused) { delegateSig = ""; delegateSince = 0; }
            else if (!sig.equals(delegateSig)) { delegateSig = sig; delegateSince = now; delegateRecoveries = 0; }
            else if (delegateSince > 0)
            {
                long idle = now - delegateSince;
                if (idle >= 30_000 && delegateRecoveries < 2)
                {
                    delegateRecoveries++;
                    // Keep the original idle clock: the 75s quest defer must fire before the
                    // generic 2-minute supervisor so quests do not get the 10-minute cooldown.
                    questHelper.recover(state);
                    DebugLog.event("QUEST_WATCHDOG", "no progress for 30s; re-armed helper ("
                        + delegateRecoveries + "/2) for " + current.label);
                }
                else if (idle >= 75_000)
                { abandon("Quest Helper stalled; no observed progress", now, 3 * Tuning.MINUTE); return; }
            }
            if (level == Watchdog.Level.REROUTE) abandon("Quest Helper made no observed progress for 4 minutes", now, 5 * Tuning.MINUTE);
            return;
        }
        if (current.kind == Method.Kind.PROCESS) production.continuePrompt(current);
        ActionMonitor.Result observed = confirmation.observe(state, now);
        if (observed == ActionMonitor.Result.CONFIRMED)
        {
            failures = 0;
            long xpDelta = Math.max(0, state.xp - lastXp);
            long coinDelta = Math.max(0, state.coins - lastCoins);
            bandit.reward(current.id, true, xpDelta, coinDelta);
            Methods.recordLearnedOutcome(current.id, true);
            lastXp = state.xp; lastCoins = state.coins;
            DebugLog.event("ACTION_CONFIRMED", current.label + ": animation, XP or product change observed");
        }
        else if (observed == ActionMonitor.Result.WAITING)
        { status = "Waiting for observed result: " + current.label; return; }
        else if (observed == ActionMonitor.Result.FAILED)
        {
            failures++;
            if(current.kind==Method.Kind.GROUND) GroundLoot.reject(current.id,now);
            bandit.penalize(current.id);
            Methods.recordLearnedOutcome(current.id, false);
            if (Methods.learnedBlocked(current.id)) { abandon("Learned method quarantined after rejected effects",now,30*Tuning.MINUTE); return; }
            DebugLog.event("ACTION_REJECTED", current.label + ": " + confirmation.reason() + "; failure=" + failures);
            if (failures >= 3)
            {
                if (config.llmEnabled && triggerSelfHeal(state, now)) return;
                abandon("Three actions without observed success", now, 5 * Tuning.MINUTE);
                return;
            }
            phase = Phase.PREPARE;
        }
        if (unlockPending && !state.combat && !state.busy && ProgressionPolicy.trains(current)) {
            abandon("Completed training segment; better eligible method available", now, Tuning.MINUTE);
            return;
        }
        if (phase==Phase.WORK && current.kind==Method.Kind.COMBAT && !state.combat && !state.busy
            && !upgrades.step(current,state,actions,config,now)) {
            status=upgrades.status(); nextAt=now+1_200; return;
        }
        if (!state.combat && current.kind != Method.Kind.QUEST && phase == Phase.WORK
            && sessionUntil > 0 && now >= sessionUntil) {
            rotation.excludeSkill(current, now, 2 * Tuning.MINUTE);
            abandon("Timed skill rotation", now, 2 * Tuning.MINUTE); return;
        }
        if (current.kind == Method.Kind.GROUND && (phase == Phase.REVENUE
            || state.items.stream().filter(i -> current.products.contains(i.name)).mapToInt(i -> i.quantity).sum() >= 3))
        {
            if (phase != Phase.REVENUE)
            {
                revenueTarget = Math.max(500, state.coins + 200); phase = Phase.REVENUE;
                DebugLog.event("REVENUE_GOAL", "Convert collected drops into coins; target=" + revenueTarget);
            }
            if (state.coins >= revenueTarget)
            { abandon("Collected resources converted to observed coins", now, Tuning.MINUTE); return; }
            if (state.pvpWorld && !worlds.prepare(Methods.get("loot_ashes"), state)) return;
            revenue.step(state, actions, revenueTarget, java.util.Set.of());
            status = revenue.status(); return;
        }
        boolean supplied = requirements.missing(current, state).isEmpty();
        if (phase == Phase.BANK || (Rs2Inventory.isFull() && supplied && current.kind != Method.Kind.PROCESS))
        {
            phase = Phase.BANK; status = "Banking current products";
            if (bank.productCount(current) == 0 && Rs2Inventory.isFull())
            {
                if (bank.depositUnrelated(state, current, java.util.Set.of()))
                { status = "Banking unrelated leftovers before this goal"; return; }
                status = "Inventory full of protected items; no destructive drop performed"; phase = Phase.COMPLETE; return;
            }
            bank.step(current, config); memory.observe();
            if (bank.productCount(current) == 0 && !Rs2Bank.isOpen())
            {
                // After banking: try a GE flip session if enabled and funded
                if (config.enableFlipping && state.coins >= config.flipMinBudget && !state.members)
                {
                    DebugLog.event("FLIP_START", "Coins=" + state.coins + " >= threshold=" + config.flipMinBudget + "; starting GE flip session");
                    flipTask.reset(); phase = Phase.FLIP;
                }
                else
                {
                    phase = Phase.SELECT;
                    if (sessionUntil > 0 && now >= sessionUntil)
                        abandon("Activity rotation after banking", now, 2 * Tuning.MINUTE);
                }
            }
            nextAt = now + Tuning.ACTION_SETTLE_MS; return;
        }
        if (phase == Phase.PREPARE || !requirements.missing(current, state).isEmpty())
        {
            phase = Phase.PREPARE;
            if (cleanupPending)
            {
                if (bank.depositUnrelated(state, current, java.util.Set.of()))
                { status = "Bank leftover items from the previous goal"; return; }
                cleanupPending = false;
            }
            supplies.step(current, state, actions, requirements.missing(current, state));
            status = "Supply prep: " + supplies.status();
            if (!supplies.failure().isEmpty()) { abandon(supplies.failure(), now, 5 * Tuning.MINUTE); return; }
            if (!requirements.missing(current, state).isEmpty()) { nextAt = now + 1_200; return; }
            DebugLog.event("PRECONDITIONS_OK", current.label + ": required supplies observed in account");
        }
        if(!setup.ready(current,state,config.preset,equipment)) {
            status=setup.status();
            if(!setup.failure().isEmpty()) abandon(setup.failure(),now,5*Tuning.MINUTE);
            nextAt=now+1_200;return;
        }
        if (!worlds.prepare(current, state))
        {
            status = "Selecting eligible world for " + current.label;
            if (++worldAttempts >= 20) abandon("No usable world or world change refused", now, 5 * Tuning.MINUTE);
            else nextAt = now + 3_000;
            return;
        }
        worldAttempts = 0;
        if (state.busy) { status = "In progress: " + current.label; return; }
        if (Rs2Bank.isOpen()) { Rs2Bank.closeBank(); return; }
        if (Rs2Shop.isOpen()) { Rs2Shop.closeShop(); return; }
        if (Rs2GrandExchange.isOpen()) { Rs2GrandExchange.closeExchange(); return; }
        if (current.kind == Method.Kind.QUEST)
        {
            if (questHelper.step(current)) { phase = Phase.DELEGATE; status = "Quest Helper: " + current.label; }
            else abandon("Quest Helper unavailable or quest could not start", now, 5 * Tuning.MINUTE);
            return;
        }
        if (!actions.localTarget(current) && state.location.distanceTo(current.location) > Tuning.TRAVEL_RADIUS)
        {
            phase = Phase.TRAVEL; status = "Travel to " + current.label + RunEnergy.summary(state);
            actions.walk(current.location); return;
        }
        phase = Phase.WORK;
        GameState latest = GameState.capture();
        if (latest == null || !requirements.missing(current, latest).isEmpty())
        { phase = Phase.PREPARE; status = "Live preconditions changed; replenish before acting"; return; }
        lastXp=latest.xp; lastCoins=latest.coins;
        confirmation.sent(current, latest, now);
        boolean submitted = current.kind == Method.Kind.OBJECT ? actions.object(current)
            : current.kind == Method.Kind.GROUND ? actions.ground(current)
            : current.kind == Method.Kind.PROCESS ? production.step(current,latest) : actions.npc(current);
        if (submitted)
        {
            metrics.action(now); emptySince = 0;
            status = "Action submitted; awaiting server evidence: " + current.label;
        }
        else
        {
            confirmation.clear();
            if (emptySince == 0) emptySince = now;
            status = "No usable target: " + current.label;
            if (config.llmEnabled && triggerSelfHeal(state, now)) return;
            if (now - emptySince > 30_000) { abandon("No targets for 30 seconds", now, Tuning.MINUTE); return; }
        }
        nextAt = now + Tuning.ACTION_SETTLE_MS;
    }
    private boolean triggerSelfHeal(GameState state,long now) throws IOException {
        String result=recovery.recover(current,state,now,emptySince,failures,agent,worlds,actions,random);
        if(result.equals("REPLAN")) abandon("Recovery requested replan",now,2*Tuning.MINUTE);
        if(result.equals("WAIT")) { emptySince=now; failures=0; nextAt=now+3_000; }
        return !result.equals("NONE");
    }
    private void choose(GameState state, long now) throws IOException
    {
        if (config.accountBuilder && !memory.known() && !state.combat && !state.busy) {
            if (initialBankDeadline==0) initialBankDeadline=now+45_000;
            if (now<initialBankDeadline) {
                supplies.inspectBank(state,actions);
                status="Inspecting bank for owned tools, gear and training supplies";
                nextAt=now+1_200;
                return;
            }
        }
        discovery.poll(state,now,config.llmEnabled,agent,chains.library());
        List<Method> candidates = eligible(state, now);
        if (candidates.isEmpty())
        { status = "All current methods temporarily unavailable; re-evaluating shortly"; nextAt = now + 5_000; return; }
        candidates = rotation.diversify(candidates, now, config.alternateMinutes * Tuning.MINUTE);
        bandit.context(state);
        // Quest suppression must run BEFORE the milestone focus, otherwise focus can return a
        // quest-only list that suppression then empties (selected == null).
        if(now<questSuppressUntil) {
            boolean hasAlternative = candidates.stream().anyMatch(m -> m.kind != Method.Kind.QUEST);
            if (hasAlternative) candidates.removeIf(m -> m.kind == Method.Kind.QUEST);
            else questSuppressUntil = 0;
        }
        availableSnapshot=candidates.stream().map(m->m.id+":"+m.kind+":"+m.skill+"@"+m.level).limit(48).collect(java.util.stream.Collectors.toList());
        if(config.accountBuilder) candidates=milestones.focus(curriculum.balance(candidates,now),state,config);
        if(candidates.isEmpty()) { status = "No eligible goal after filters; re-evaluating"; nextAt = now + 5_000; return; }
        final List<Method> ranked = config.taskMode.equals("MONEY") ? candidates : ProgressionPolicy.frontier(candidates,method -> outcomes.rate(method,state));
        boolean learning = config.accountBuilder && !config.taskMode.equals("MONEY");
        if (learning) goalLearning.learn(state,ranked,method -> stocked(method,state));
        var scores = new java.util.HashMap<Method,Double>();
        for (Method method : ranked) {
            double experience = learning ? goalLearning.bonus(method,state,stocked(method,state))
                : outcomes.bonus(method,state);
            scores.put(method,planner.score(method,ranked,state,previous,learning?null:bandit,random)
                +experience+curriculum.bonus(method,state,config)
                +(learning?milestones.preference(method)+HumanWorkflowPrior.bonus(method,state,stocked(method,state)):0));
        }
        Method selected=ranked.stream().max(java.util.Comparator.comparingDouble(scores::get)).orElse(null);
        if(selected==null) { status = "No eligible goal after scoring; re-evaluating"; nextAt = now + 5_000; return; }
        if (config.llmEnabled && ranked.size()>1) {
            double bestScore=scores.get(selected);
            List<String> close=ranked.stream().filter(method -> scores.get(method)>=bestScore-10)
                .map(method -> method.id).sorted().collect(Collectors.toList());
            if (close.size()>1) {
                if (suggestion==null) {
                    suggestedChoices=close;
                    suggestionDeadline=now+10_000;
                    suggestion=agent.suggest(close,"Live levels=" + state.levels + "; coins=" + state.coins
                        + "; bank inspected=" + memory.known() + "; local top=" + selected.id);
                }
                if (close.equals(suggestedChoices) && !suggestion.isDone() && now<suggestionDeadline) {
                    status="Comparing close eligible goals: " + String.join(", ",close);
                    nextAt=now+400;
                    return;
                }
                if (close.equals(suggestedChoices) && suggestion.isDone()) {
                    String suggested=suggestion.getNow(null);
                    for (Method candidate : ranked) if (candidate.id.equals(suggested) && close.contains(candidate.id)) {
                        selected=candidate;
                        DebugLog.event("AGENT_CHOICE","Fresh shortlist -> "+selected.id);
                        break;
                    }
                }
            }
        }
        suggestion=null;
        lastXp = state.xp; lastCoins = state.coins;
        rotation.selected(selected,now);
        supervisor.selection(selected,state,memory.items(),config.llmEnabled);
        outcomes.begin(selected,state,now);
        if (learning) goalLearning.begin(selected,state,stocked(selected,state),now);
        nextProgressionCheck=now+30_000;unlockPending=false;
        current = selected; selectedAt = now; leaseAt = 0; failures = 0; emptySince = 0; worldAttempts = 0;
        delegateSig = ""; delegateSince = 0; delegateRecoveries = 0;
        curriculum.note(selected, now);
        sessionUntil = now + config.alternateMinutes * Tuning.MINUTE;
        curriculum.bonus(selected, state, config);
        DebugLog.event("CURRICULUM", curriculum.aim() + " -> " + selected.id);
        phase = Phase.PREPARE; supplies.reset(); metrics.taskSwitches++;
        List<ItemNeed> missing = requirements.missing(current, state);
        status = "Goal: " + current.label + (missing.isEmpty() ? "; ready" : "; missing " + missing);
        DebugLog.event("DECISION", status + "; eligible finalists="
            + ranked.stream().map(m -> m.id).limit(8).collect(Collectors.joining(","))
            + "; level=" + state.levels.get(current.skill)
            + "; training tier=" + ProgressionPolicy.trainingTier(current)
            + "; goal Q bonus=" + Math.round(goalLearning.bonus(current,state,stocked(current,state)))
            + "; human workflow bonus=" + (learning ? HumanWorkflowPrior.bonus(current,state,stocked(current,state)) : 0)
            + "; observed xp/h=" + Math.round(outcomes.rate(current,state))
            + "; quest state=" + state.quests.getOrDefault(current.label, "n/a"));
    }
    private boolean stocked(Method method,GameState state) {
        return BuilderBudget.affordable(method,state,memory.items(),id -> 1_000_000);
    }

    private List<Method> eligible(GameState state,long now) throws IOException {
        var excluded = rotation.excluded(now);
        List<Method> candidates = goals.eligible(state,config,excluded);
        for (var iterator = candidates.iterator(); iterator.hasNext();) {
            Method method = iterator.next();
            if (shared.quarantined(method.id,now) || (config.accountBuilder && !config.taskMode.equals("MONEY")
                && !BuilderBudget.affordable(method,state,memory.items(),id -> TradeService.unitPrice(id,true))
                && RecipeChains.plan(method,state,memory.items(),excluded,id -> TradeService.unitPrice(id,true)) == null))
                iterator.remove();
        }
        return candidates;
    }

    private void abandon(String reason, long now, long cooldown) throws IOException
    {
        if(observed!=null) {
            boolean skipped = reason.startsWith("Skipped");
            outcomes.finish(observed,now,skipped);
            goalLearning.finish(observed,now,skipped);
        }
        chains.reset(); questHelper.stopOwned(); confirmation.clear(); upgrades.reset();
        cleanupPending = true; delegateSig = ""; delegateSince = 0; delegateRecoveries = 0;
        if (current != null && current.kind == Method.Kind.QUEST
            && (reason.contains("stalled") || reason.startsWith("Chain failed")
                || reason.startsWith("Supply") || reason.contains("no observed progress")))
        {
            questFailures++;
            if (questFailures >= 2)
            {
                questFailures = 0;
                questSuppressUntil = now + 10 * Tuning.MINUTE;
                DebugLog.event("QUEST_COOLDOWN", "Two quest failures; skilling/combat only for 10 minutes");
            }
        }
        if (current != null)
        {
            if(observed!=null && (reason.startsWith("Setup failed") || reason.startsWith("Supply") || reason.startsWith("Chain failed") || reason.startsWith("No observed")))
                supervisor.failed(current,observed,now,phase.name(),reason,memory.items(),config.llmEnabled);
            previous = current.id; rotation.exclude(current.id, now, cooldown);
            if (reason.startsWith("No targets") || reason.startsWith("No observed progress"))
                Methods.recordLearnedOutcome(current.id,false);
            if (!reason.startsWith("Skipped") && !reason.contains("rotation") && !reason.contains("converted") && !reason.startsWith("Completed") && !reason.startsWith("Quest completed"))
                bandit.penalize(current.id);
            DebugLog.event("REPLAN", current.label + ": " + reason + "; cooldownMs=" + cooldown);
        }
        shared.release(owner); current = null; phase = Phase.SELECT; supplies.reset(); nextAt = now + 500;
    }
    public void close() throws IOException { closed = true; growth.close(); supervisor.close(); chains.reset(); flipTask.cancel(); bandit.save(); questHelper.close(); shared.release(owner); }
}
