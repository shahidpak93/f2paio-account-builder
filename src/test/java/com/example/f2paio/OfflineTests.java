package com.example.f2paio;

import com.sun.net.httpserver.HttpServer;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.client.plugins.PluginDescriptor;

public final class OfflineTests
{
    private static int checks;
    private static void check(boolean value, String message)
    {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception
    {
        if (args.length > 0 && args[0].equals("lease"))
        {
            boolean won = new SharedStore(Path.of(args[1])).lease("spot", args[2], 60_000, Long.parseLong(args[3]));
            System.out.print(won ? "WON" : "LOST"); return;
        }
        Path root = FilesStore.ROOT;
        check(!Files.exists(root.resolve("panel.json")), "Tests require a fresh isolated user.home");
        LootRegressionTests.run();
        testConfig(root);
        testPersona();
        testWatchdog(root);
        testSafetyMonitor(root);
        testTiming();
        testSharedStore(root);
        testMotion();
        testAutonomyAndProgress();
        testRequirementsAndConfirmation();
        testAgent();
        testSecret();
        LearningTests.run();
        ChainTests.run();
        AccountBuilderTests.run();
        RunEnergyTests.run();
        BankCurriculumTests.run();
        RuntimeFixTests.run();
        AccountKnowledgeTests.run();
        AiBridgeTests.run();
        PluginDescriptor descriptor = F2pAioPlugin.class.getAnnotation(PluginDescriptor.class);
        check(descriptor.isExternal() && !descriptor.enabledByDefault(), "Plugin must opt into external discovery");
        check(descriptor.minClientVersion().equals("2.6.54"), "Minimum client version");
        System.out.println("PASS " + checks + " checks");
    }

    private static void testConfig(Path root) throws Exception
    {
        F2pAioConfig c = new F2pAioConfig(); c.validate();
        c.llmEnabled = true; c.llmEndpoint = "http://127.0.0.1:1234/test"; c.validate();
        for (String url : List.of("http://evil.example/test", "https://key@evil.example/test",
            "https://example.com/test?key=secret", "file:///tmp/key"))
        {
            c.llmEndpoint = url; boolean rejected = false;
            try { c.validate(); } catch (IllegalArgumentException ex) { rejected = true; }
            check(rejected, "Reject insecure/credential-bearing endpoint");
        }
        c = new F2pAioConfig(); c.sessionMax = 33;
        FilesStore.write(root.resolve("panel.json"), c);
        check(FilesStore.read(root.resolve("panel.json"), F2pAioConfig.class).sessionMax == 33, "Panel config round trip");
        check(!Files.readString(root.resolve("panel.json")).contains("llmKey"), "No secret field in config");
        F2pAioConfig copy = c.copy(); copy.enabledMethods.clear();
        check(!c.enabledMethods.isEmpty(), "Session snapshot does not share mutable activities");
        c.autonomous = false; c.selectedMethod = "bad"; boolean rejected = false;
        try { c.validate(); } catch (IllegalArgumentException ex) { rejected = true; }
        check(rejected, "Unknown method rejected");
    }

    private static void testPersona() throws Exception
    {
        Persona a = Persona.load("Test / Account", "seed-a");
        Persona b = Persona.load("Test / Account", "seed-b");
        check(a.seed == b.seed && a.speed == b.speed, "Existing persona remains stable across seed changes");
        check(!a.id.contains("/") && a.id.length() == 24, "Persona path does not contain account name");
        try (var files = Files.list(FilesStore.ROOT.resolve("personas")))
        {
            check(files.count() == 1, "One persisted persona");
        }
    }

    private static void testWatchdog(Path root) throws Exception
    {
        Path stop = root.resolve("STOP");
        Watchdog w = new Watchdog(stop, 0);
        check(w.level(44_999) == Watchdog.Level.OK, "Before retry threshold");
        check(w.level(45_000) == Watchdog.Level.RETRY, "Retry threshold");
        check(w.level(240_000) == Watchdog.Level.REROUTE, "Reroute threshold");
        check(w.level(20 * Tuning.MINUTE) == Watchdog.Level.ABANDON, "Abandon threshold");
        check(w.level(60 * Tuning.MINUTE) == Watchdog.Level.LOGOUT, "Logout threshold");
        w.observe("xp=10", 1_000);
        check(w.level(1_001) == Watchdog.Level.OK, "Observed progress resets idle");
        for (int i = 0; i < 40; i++) w.dialogue("same option");
        check(w.level(1_001) == Watchdog.Level.ABANDON, "Repeated dialogue escalates");
        Files.writeString(stop, "stop");
        check(w.level(1_001) == Watchdog.Level.KILL, "Kill overrides all states");
        Files.delete(stop);
    }

    private static void testTiming()
    {
        check(Scheduler.withinHours(22, 6, LocalTime.of(23, 0)), "Overnight start");
        check(Scheduler.withinHours(22, 6, LocalTime.of(5, 0)), "Overnight end");
        check(!Scheduler.withinHours(22, 6, LocalTime.NOON), "Overnight exclusion");
        check(Scheduler.withinHours(0, 24, LocalTime.of(23, 59)), "Full-day window");
        Random r = new Random(42); double total = 0, squared = 0;
        for (int i = 0; i < 10_000; i++)
        {
            double value = Distributions.exGaussian(r, 190, 55, 120);
            check(value > 0 && Double.isFinite(value), "Finite positive reaction");
            total += value; squared += value * value;
        }
        double mean = total / 10_000;
        check(Math.sqrt(squared / 10_000 - mean * mean) / mean > 0.22, "Reaction distribution variation");
        F2pAioConfig config = new F2pAioConfig(); config.sessionMax = 1;
        Persona p = new Persona(); p.endurance = 1;
        Scheduler s = new Scheduler(r, p, config, 1_000);
        check(!s.ended(60_999) && s.ended(61_000), "Hard session cap");
        Bandit bandit = new Bandit();
        for (int i = 0; i < 200; i++) { bandit.reward("good", true); bandit.reward("bad", false); }
        check(bandit.score("good", r) > bandit.score("bad", r), "Bandit learns progress outcomes");
        GoalGraph graph = new GoalGraph();
        for (int i = 0; i < 100; i++)
            check(graph.choose(Methods.all(), "trees", "trees", r, bandit) != null, "Stable rank comparator");
    }

    private static void testSafetyMonitor(Path root) throws Exception
    {
        Path flag = root.resolve("independent-stop");
        java.util.concurrent.CountDownLatch killed = new java.util.concurrent.CountDownLatch(1);
        var blockedWorker = Executors.newSingleThreadExecutor();
        java.util.concurrent.CountDownLatch blocked = new java.util.concurrent.CountDownLatch(1);
        try (SafetyMonitor monitor = new SafetyMonitor(() -> {
            if (Files.exists(flag)) { killed.countDown(); blockedWorker.shutdownNow(); }
        }))
        {
            blockedWorker.submit(() -> {
                blocked.countDown();
                try { new java.util.concurrent.CountDownLatch(1).await(); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            });
            check(blocked.await(2, TimeUnit.SECONDS), "Worker blocked before emergency stop");
            Files.writeString(flag, "stop");
            check(killed.await(2, TimeUnit.SECONDS), "Independent monitor detects STOP during a blocked worker");
            check(blockedWorker.awaitTermination(2, TimeUnit.SECONDS), "Emergency stop interrupts blocked worker");
        }
        finally { blockedWorker.shutdownNow(); Files.deleteIfExists(flag); }
    }

    private static void testSharedStore(Path root) throws Exception
    {
        SharedStore a = new SharedStore(root.resolve("shared-test"));
        SharedStore b = new SharedStore(root.resolve("shared-test"));
        check(a.lease("trees", "owner-a", 1_000, 1_000), "Acquire lease");
        check(!b.lease("trees", "owner-b", 1_000, 1_001), "Exclude concurrent owner");
        check(a.lease("trees", "owner-a", 1_000, 1_200), "Renew owner lease");
        check(b.lease("trees", "owner-b", 1_000, 2_201), "Expired lease transferable");
        a.failure("broken", 3_000);
        check(b.quarantined("broken", 3_001), "Shared failure quarantine");
        check(!b.quarantined("broken", 3_000 + Tuning.ABANDON_MS), "Failure quarantine expires");
        Path multi = root.resolve("shared-processes");
        String java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        String cp = System.getProperty("java.class.path"); String now = Long.toString(System.currentTimeMillis());
        List<Process> processes = new ArrayList<>();
        for (int i = 0; i < 3; i++)
            processes.add(new ProcessBuilder(java, "-cp", cp, OfflineTests.class.getName(), "lease",
                multi.toString(), "owner-" + i, now).start());
        int winners = 0;
        for (Process process : processes)
        {
            check(process.waitFor(15, TimeUnit.SECONDS), "Lease child completed");
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            check(process.exitValue() == 0, "Lease child succeeded");
            if (output.equals("WON")) winners++;
        }
        check(winners == 1, "Exactly one process owns the spot");
    }

    private static void testMotion()
    {
        Recorder.Sample first = new Recorder.Sample(MouseEvent.MOUSE_MOVED, 0, 0, 0);
        Recorder.Sample mid = new Recorder.Sample(MouseEvent.MOUSE_MOVED, 50, 5, 50);
        Recorder.Sample last = new Recorder.Sample(MouseEvent.MOUSE_PRESSED, 100, 0, 100);
        Point start = new Point(10, 20), end = new Point(10, 220);
        check(HumanInput.transform(first, last, first, start, end).equals(start), "Motion starts at live pointer");
        check(HumanInput.transform(first, last, last, start, end).equals(end), "Motion ends at current target");
        check(HumanInput.transform(first, last, mid, start, end).equals(new Point(0, 120)), "Motion rotates curvature");
    }

    private static void testAgent() throws Exception
    {
        var executor = Executors.newCachedThreadPool();
        HttpServer server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/ollama", exchange -> {
            requests.incrementAndGet();
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String reply = request.contains("invalid-response") ? "{\"response\":\"{\\\"method\\\":\\\"evil\\\"}\"}"
                : "{\"response\":\"{\\\"method\\\":\\\"trees\\\"}\"}";
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.createContext("/openai", exchange -> {
            boolean bearer = "Bearer test-secret".equals(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = (bearer ? "{\"choices\":[{\"message\":{\"content\":\"{\\\"method\\\":\\\"trees\\\"}\"}}]}" : "{}")
                .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.createContext("/oversized", exchange -> {
            byte[] bytes = new byte[Tuning.MAX_RESPONSE_BYTES + 100];
            exchange.sendResponseHeaders(200, bytes.length);
            try { exchange.getResponseBody().write(bytes); } catch (java.io.IOException ex) { }
            exchange.close();
        });
        server.start();
        try
        {
            F2pAioConfig c = new F2pAioConfig(); c.llmEnabled = true; c.localFallbackEnabled = false;
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            c.llmEndpoint = base + "/ollama";
            try (AgentBus bus = new AgentBus(c, ""))
            {
                check("trees".equals(bus.suggest(List.of("trees"), "test").get(10, TimeUnit.SECONDS)), "Ollama response");
                check(bus.suggest(List.of("trees"), "invalid-response").get(10, TimeUnit.SECONDS) == null,
                    "Reject method outside allowlist");
                for (int i = 0; i < 10; i++) bus.suggest(List.of("trees"), "test").get(10, TimeUnit.SECONDS);
                check(bus.status().contains("budget"), "Hourly request cap");
                check(requests.get() == 10, "Budget prevents HTTP requests");
            }
            c.llmBackend = "OPENAI_COMPATIBLE"; c.llmEndpoint = base + "/openai";
            try (AgentBus bus = new AgentBus(c, "test-secret"))
            {
                check("trees".equals(bus.suggest(List.of("trees"), "test").get(10, TimeUnit.SECONDS)),
                    "OpenAI-compatible response and bearer authentication");
            }
            c.llmEndpoint = base + "/oversized";
            try (AgentBus bus = new AgentBus(c, ""))
            {
                check(bus.suggest(List.of("trees"), "test").get(10, TimeUnit.SECONDS) == null, "Reject oversized body");
                check(!bus.status().contains("test-secret"), "No secret in error status");
            }
        }
        finally { server.stop(0); executor.shutdownNow(); }
    }

    private static void testSecret() throws Exception
    {
        SecretStore.save("local-test-secret-123");
        check(SecretStore.load().equals("local-test-secret-123"), "Windows DPAPI round trip");
        check(!Files.readString(FilesStore.ROOT.resolve("llm-key.dpapi")).contains("local-test-secret"), "Encrypted at rest");
        SecretStore.forget();
        check(SecretStore.load().isEmpty(), "Forget key");
    }

    private static GameState snapshot(int woodcutting, int xp, String quest, long coins)
    {
        var levels = new java.util.EnumMap<net.runelite.api.Skill, Integer>(net.runelite.api.Skill.class);
        var experience = new java.util.EnumMap<net.runelite.api.Skill, Integer>(net.runelite.api.Skill.class);
        for (var skill : net.runelite.api.Skill.values())
        {
            if (skill == net.runelite.api.Skill.OVERALL) continue;
            levels.put(skill, 1); experience.put(skill, 0);
        }
        levels.put(net.runelite.api.Skill.WOODCUTTING, woodcutting);
        experience.put(net.runelite.api.Skill.WOODCUTTING, xp);
        return new GameState("offline", new net.runelite.api.coords.WorldPoint(3200, 3400, 0),
            xp, 1, false, false, 100, false, levels, experience, java.util.Map.of("Cook's Assistant", quest), coins);
    }

    private static void testAutonomyAndProgress()
    {
        F2pAioConfig c = new F2pAioConfig(); c.enabledMethods.clear(); c.selectedMethod = "not-a-selection";
        c.validate();
        GameState initial = snapshot(15, 2_500, "NOT_STARTED", 100);
        var eligible = new GoalGraph().eligible(initial, c, java.util.Set.of());
        check(eligible.stream().anyMatch(m -> m.id.equals("oak")), "Auto eligibility ignores old checkbox list");
        check(eligible.stream().noneMatch(m -> m.id.equals("yew")), "Auto planner respects level requirements");
        var woods = eligible.stream().filter(m -> m.skill == net.runelite.api.Skill.WOODCUTTING)
            .collect(java.util.stream.Collectors.toList());
        check(new AutonomousPlanner().choose(woods, initial, "").id.equals("oak"), "Auto tier progression selects oak");
        ProgressTracker tracker = new ProgressTracker(); tracker.observe(initial);
        tracker.observe(snapshot(16, 2_800, "FINISHED", 80));
        check(tracker.snapshot().xpGained == 300, "Dashboard XP difference");
        check(tracker.snapshot().levelUps == 1, "Dashboard level-up count");
        check(tracker.snapshot().newQuests == 1, "Dashboard quest completion");
        check(tracker.snapshot().coinChange == -20, "Coin delta includes spending, not fake profit");
        check(DebugLog.recent().stream().anyMatch(line -> line.contains("LEVEL_UP")), "Level-up event recorded");
        check(DebugLog.recent().stream().anyMatch(line -> line.contains("QUEST_COMPLETE")), "Quest-completion event recorded");
        tracker.reset(); tracker.observe(snapshot(16, 2_800, "FINISHED", 80));
        check(tracker.snapshot().xpGained == 0 && tracker.snapshot().newQuests == 0, "New session rebases progress");
    }

    private static void testRequirementsAndConfirmation()
    {
        GameState state = snapshot(1, 50, "FINISHED", 848);
        state.equipment = List.of(new ResourceItem(1265, "Bronze pickaxe", 1));
        check(!ToolPolicy.has("axe", state), "Regression: equipped pickaxe must not satisfy axe requirement");
        check(ToolPolicy.has("pickaxe", state), "Equipped pickaxe satisfies mining");
        check(new Requirements().missing(Methods.get("trees"), state).stream().anyMatch(n -> n.id == 1351),
            "Missing axe creates an acquisition subgoal before any tree click");
        state.items = List.of(new ResourceItem(1351, "Bronze axe", 1));
        check(ToolPolicy.has("axe", state), "Carried usable axe allows chopping");
        check(!ToolPolicy.usable("axe", 1359, 1), "Cannot use rune axe at level 1 Woodcutting");
        check(ToolPolicy.usable("axe", 1359, 41), "Rune axe unlocks at Woodcutting 41");
        state.items = List.of(new ResourceItem(304, "Small fishing net", 1));
        check(!ToolPolicy.has("Small fishing net", state), "Noted fishing tools cannot satisfy requirements");
        check(SupplyPolicy.next(false, false, 0, 0, 0, 100) == SupplyPolicy.Next.INSPECT_BANK,
            "Unknown bank is inspected before purchase or looting");
        check(SupplyPolicy.next(false, true, 1, 0, 0, 100) == SupplyPolicy.Next.WITHDRAW_ITEM,
            "Use bank item before spending coins");
        check(SupplyPolicy.next(false, true, 0, 500, 0, 100) == SupplyPolicy.Next.WITHDRAW_COINS,
            "Use known bank coins before funding");
        check(SupplyPolicy.next(false, true, 0, 0, 0, 100) == SupplyPolicy.Next.EARN_COINS,
            "No money creates funding subgoal");
        check(SupplyPolicy.next(false, true, 0, 0, 100, 100) == SupplyPolicy.Next.BUY,
            "Funded requirement permits buying");
        check(SupplyPolicy.next(true, false, 0, 0, 0, 100) == SupplyPolicy.Next.READY,
            "Verified inventory fulfills precondition");
        state.items = List.of();
        ActionMonitor action = new ActionMonitor(); action.sent(Methods.get("trees"), state, 1_000);
        check(action.observe(state, 12_999) == ActionMonitor.Result.WAITING,
            "Submitted click alone is never confirmed success");
        check(action.observe(state, 13_000) == ActionMonitor.Result.FAILED, "No server evidence times out");
        action.sent(Methods.get("trees"), state, 20_000);
        action.feedback("You need an axe to chop this tree.");
        check(action.observe(state, 20_100) == ActionMonitor.Result.FAILED, "Game rejection stops repeated clicks");
        action.sent(Methods.get("trees"), state, 30_000);
        check(action.observe(snapshot(1, 75, "FINISHED", 848), 30_600) == ActionMonitor.Result.CONFIRMED,
            "Observed Woodcutting XP confirms action");
        action.sent(Methods.get("trees"), state, 40_000);
        state.moving = true;
        check(action.observe(state, 40_100) == ActionMonitor.Result.WAITING, "Movement alone does not prove chopping");
        state.animating = true;
        check(action.observe(state, 40_600) == ActionMonitor.Result.CONFIRMED, "Animation confirms action");
        check(Methods.all().size() >= 35, "Expanded executable catalogue");
        TaskRotation rotation = new TaskRotation(); rotation.exclude("trees", 1000, 600_000);
        var config = new F2pAioConfig();
        check(new GoalGraph().eligible(state, config, rotation.excluded(1001)).stream().noneMatch(m -> m.id.equals("trees")),
            "Skip excludes current task from the next plan");
        check(!rotation.excluded(601_000).contains("trees"), "Skipped task becomes eligible after cooldown");
        check(new GoalGraph().eligible(state, config, java.util.Set.of()).stream().noneMatch(m -> m.id.equals("cooks")),
            "Already completed quest is excluded using actual account state");
        check(new Requirements().missing(Methods.get("cook_shrimp"), state).stream().anyMatch(n -> n.id == 317),
            "Cooking creates a raw-fish acquisition goal");
        var a = net.runelite.http.api.worlds.World.builder().id(301).players(200)
            .types(java.util.EnumSet.of(net.runelite.http.api.worlds.WorldType.PVP)).build();
        var b = net.runelite.http.api.worlds.World.builder().id(308).players(400)
            .types(java.util.EnumSet.of(net.runelite.http.api.worlds.WorldType.PVP)).build();
        var c = net.runelite.http.api.worlds.World.builder().id(310).players(1000)
            .types(java.util.EnumSet.of(net.runelite.http.api.worlds.WorldType.PVP,
                net.runelite.http.api.worlds.WorldType.MEMBERS)).build();
        check(WorldLootPolicy.select(List.of(a, b, c), true).getId() == 308,
            "PvP looting selects most populated eligible F2P world, not a hardcoded world");
    }
}

