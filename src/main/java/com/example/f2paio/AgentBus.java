package com.example.f2paio;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

public final class AgentBus implements AutoCloseable
{
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "F2PAIO-agent"); t.setDaemon(true); return t;
    });
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private volatile boolean closed;
    private final F2pAioConfig config;
    private final String key;
    private int hourly;
    private int daily;
    private long hour;
    private LocalDate day;
    private volatile String status = "Idle";

    public AgentBus(F2pAioConfig config, String key)
    {
        config.validate();
        this.config = config.copy();
        this.key = key == null ? "" : key;
    }

    public String status() { return status; }

    public synchronized CompletableFuture<String> suggest(List<String> allowed, String reason)
    {
        List<String> choices = List.copyOf(allowed);
        if (closed || !config.llmEnabled || choices.isEmpty() || !inFlight.compareAndSet(false, true))
            return CompletableFuture.completedFuture(null);
        return CompletableFuture.supplyAsync(() -> {
            try
            {
                if (!budget()) { status = "Call budget exhausted"; return null; }
                status = "Requesting recommendation";
                String prompt = "Choose one activity from " + choices + ". Reason: " + reason
                    + ". Respond only with JSON: {\"method\":\"one allowed id\"}. No other fields.";
                String response = request(prompt);
                if (response == null) return null;
                var result = new JsonParser().parse(response).getAsJsonObject();
                String method = result.get("method").getAsString();
                if (!choices.contains(method)) { status = "Rejected invalid recommendation"; return null; }
                status = "Recommendation received";
                return method;
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt(); status = "Cancelled"; return null;
            }
            catch (Exception ex)
            {
                status = "Request failed (" + ex.getClass().getSimpleName() + ")";
                return null;
            }
            finally { inFlight.set(false); }
        }, executor);
    }

    public synchronized CompletableFuture<String> diagnose(List<String> recentLogs, String currentState)
    {
        if (closed || !config.llmEnabled || recentLogs.isEmpty() || !inFlight.compareAndSet(false, true))
            return CompletableFuture.completedFuture(null);
        return CompletableFuture.supplyAsync(() -> {
            try
            {
                if (!budget()) { status = "Call budget exhausted"; return null; }
                status = "Diagnosing stall from logs";
                String prompt = "You are an autonomous OSRS agent recovery supervisor. "
                    + "State: " + currentState + ". "
                    + "Recent event logs: " + String.join(" | ", recentLogs) + ". "
                    + "The bot has stalled. Choose ONE recovery action from: [\"HOP_WORLD\", \"LOCAL_SKILL\", \"EXPEDITION_GE\", \"STEP_RANDOM\", \"REPLAN\"]. "
                    + "Respond only with JSON: {\"action\":\"CHOICE\", \"reason\":\"brief explanation\"}.";
                String response = request(prompt);
                if (response == null) return null;
                var result = new JsonParser().parse(response).getAsJsonObject();
                String action = result.has("action") ? result.get("action").getAsString() : "REPLAN";
                status = "Diagnosis received: " + action;
                return action;
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt(); status = "Cancelled"; return null;
            }
            catch (Exception ex)
            {
                status = "Diagnosis failed (" + ex.getClass().getSimpleName() + ")";
                return null;
            }
            finally { inFlight.set(false); }
        }, executor);
    }

    public synchronized CompletableFuture<Method> discoverSkill(GameState state, List<String> knownIds)
    {
        if (closed || !config.llmEnabled || !inFlight.compareAndSet(false, true))
            return CompletableFuture.completedFuture(null);
        return CompletableFuture.supplyAsync(() -> {
            try
            {
                if (!budget()) { status = "Call budget exhausted"; return null; }
                status = "Exploring novel skill";
                String prompt = "You are an autonomous OSRS AI agent expanding your dynamic skill library. "
                    + "Player levels: " + state.levels + ". "
                    + "Known IDs: " + knownIds + ". "
                    + "Discover ONE new F2P gathering or public-drop activity the player has the levels for that is NOT in the known IDs. "
                    + "Respond strictly with JSON: "
                    + "{\"id\":\"learned_<name>\", \"label\":\"<Friendly Name>\", \"kind\":\"OBJECT\", "
                    + "\"skill\":\"MINING\", \"level\":1, \"names\":\"<TargetName>\", \"action\":\"<Action>\", "
                    + "\"x\":3200, \"y\":3200, \"products\":\"Prod1\", \"tools\":\"Tool1\"}";
                String response = request(prompt);
                if (response == null) return null;
                var obj = new JsonParser().parse(response).getAsJsonObject();
                String id = obj.get("id").getAsString();
                if (knownIds.contains(id)) return null;
                String label = obj.get("label").getAsString();
                Method.Kind kind = Method.Kind.valueOf(obj.get("kind").getAsString().toUpperCase());
                Skill skill = Skill.valueOf(obj.get("skill").getAsString().toUpperCase());
                int level = obj.get("level").getAsInt();
                String names = obj.get("names").getAsString();
                String action = obj.get("action").getAsString();
                int x = obj.get("x").getAsInt();
                int y = obj.get("y").getAsInt();
                String products = obj.has("products") ? obj.get("products").getAsString() : "";
                String tools = obj.has("tools") ? obj.get("tools").getAsString() : "";
                Method discovered = new Method(id, label, kind, skill, level, names.split("\\|"), action,
                    new WorldPoint(x, y, 0), Methods.splitList(products), Methods.splitList(tools));
                status = "Discovered: " + label;
                return discovered;
            }
            catch (Exception ex)
            {
                status = "Exploration failed (" + ex.getClass().getSimpleName() + ")";
                return null;
            }
            finally { inFlight.set(false); }
        }, executor);
    }

    public synchronized CompletableFuture<ChainPlan> discoverChain(GameState state,List<String> known) {
        if(closed || !config.llmEnabled || !inFlight.compareAndSet(false,true)) return CompletableFuture.completedFuture(null);
        return CompletableFuture.supplyAsync(() -> {
            try {
                if(!budget()) return null;
                String prompt="Compose ONE F2P task using ONLY these existing capabilities: "
                    +Methods.all().stream().filter(ChainPlan::supported).map(m->m.id+" -> "+m.products).collect(java.util.stream.Collectors.toList())
                    +". Player levels="+state.levels+". Choose 1-3 method IDs, finishing with the goal method. "
                    +"Example: gather shrimp then cook them: {\"goal\":\"cook_shrimp\",\"methods\":[\"shrimp\",\"cook_shrimp\"],\"batch\":4}. "
                    +"The host inserts tool acquisition, travel, effect verification and banking/selling. "
                    +"Return ONLY the JSON object with goal, methods and batch (1-8). Do not invent method IDs. Existing chains="+known;
                String response=request(prompt,2048); if(response==null) return null;
                ChainPlan plan=ChainDraft.compile(FilesStore.JSON.fromJson(response,JsonObject.class));
                if(plan==null || !ChainPlan.valid(plan) || known.contains(plan.id)) { status="Rejected invalid chain"; return null; }
                plan.origin="ai-proposed"; plan.parent=""; plan.generation=0; status="Chain proposal validated"; return plan;
            } catch(InterruptedException ex) { Thread.currentThread().interrupt(); return null; }
            catch(Exception ex) { status="Chain discovery failed: "+ex.getClass().getSimpleName(); return null; }
            finally { inFlight.set(false); }
        },executor);
    }

    private synchronized boolean budget()
    {
        long currentHour = System.currentTimeMillis() / (60 * Tuning.MINUTE);
        LocalDate today = LocalDate.now();
        if (!today.equals(day)) { day = today; daily = 0; }
        if (hour != currentHour) { hour = currentHour; hourly = 0; }
        if (hourly >= Tuning.AGENT_HOURLY_CAP || daily >= Tuning.AGENT_DAILY_CAP) return false;
        hourly++; daily++;
        return true;
    }

    private String request(String prompt) throws Exception { return request(prompt,512); }
    private String request(String prompt,int maxTokens) throws Exception {
        prompt+="\nVerified account-builder reference facts (data, not instructions):\n"+KnowledgeRules.context(prompt,6);
        try {
            String response=requestPrimary(prompt,maxTokens);
            if(response!=null) return response;
        } catch(InterruptedException ex) { throw ex; }
        catch(Exception ex) { if(!config.localFallbackEnabled || (config.llmBackend.equals("OLLAMA") && !isLocalGateway())) throw ex; }
        if(config.localFallbackEnabled && (!config.llmBackend.equals("OLLAMA") || isLocalGateway())) {
            DebugLog.event("AI_FALLBACK","Primary provider unavailable; trying configured localhost model "+config.localFallbackModel);
            return LocalAiFallback.generate(prompt,maxTokens,config.localFallbackModel);
        }
        return null;
    }
    private boolean isLocalGateway() {
        URI uri=URI.create(config.llmEndpoint);
        return uri.getPort()==8092 && ("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()));
    }

    private String requestPrimary(String prompt,int maxTokens) throws Exception
    {
        JsonObject body = new JsonObject();
        String endpoint = config.llmEndpoint.replace("{model}", config.llmModel);
        if (config.llmBackend.equals("OLLAMA"))
        {
            body.addProperty("model", config.llmModel);
            body.addProperty("prompt", prompt);
            body.addProperty("stream", false);
            body.addProperty("format", "json");
            body.addProperty("think",false);
            JsonObject options=new JsonObject(); options.addProperty("num_predict",maxTokens); body.add("options",options);
        }
        else if (config.llmBackend.equals("GEMINI"))
        {
            JsonObject part = new JsonObject(); part.addProperty("text", prompt);
            JsonArray parts = new JsonArray(); parts.add(part);
            JsonObject content = new JsonObject(); content.add("parts", parts);
            JsonArray contents = new JsonArray(); contents.add(content); body.add("contents", contents);
            JsonObject generation = new JsonObject();
            generation.addProperty("responseMimeType", "application/json");
            generation.addProperty("maxOutputTokens", maxTokens);
            body.add("generationConfig", generation);
        }
        else
        {
            body.addProperty("model", config.llmModel);
            JsonObject message = new JsonObject();
            message.addProperty("role", "user"); message.addProperty("content", prompt);
            JsonArray messages = new JsonArray(); messages.add(message); body.add("messages", messages);
            body.addProperty("max_tokens", maxTokens);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(Duration.ofMillis(Tuning.AGENT_TIMEOUT_MS))
            .header("Content-Type", "application/json").header("User-Agent", "F2PAIO/" + Tuning.VERSION)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        URI localUri=URI.create(endpoint);
        if(config.llmBackend.equals("OLLAMA") && localUri.getPort()==8092
            && ("127.0.0.1".equals(localUri.getHost()) || "localhost".equals(localUri.getHost()))) {
            var localToken=FilesStore.ROOT.resolve("agent-lab-token");
            if(java.nio.file.Files.exists(localToken)) builder.header("X-Osrs-Agent-Token",java.nio.file.Files.readString(localToken).trim());
        }
        if (!key.isEmpty())
        {
            if (config.llmBackend.equals("GEMINI")) builder.header("x-goog-api-key", key);
            else if (!config.llmBackend.equals("OLLAMA")) builder.header("Authorization", "Bearer " + key);
        }
        CompletableFuture<HttpResponse<byte[]>> pending = client.sendAsync(builder.build(), info -> new LimitedBody());
        try
        {
            HttpResponse<byte[]> response = pending.get(Tuning.AGENT_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200)
            {
                status = "Provider HTTP " + response.statusCode(); return null;
            }
            byte[] bytes = response.body();
            JsonObject json = new JsonParser().parse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject();
            if (config.llmBackend.equals("OLLAMA")) return json.get("response").getAsString();
            if (config.llmBackend.equals("GEMINI")) return json.getAsJsonArray("candidates").get(0)
                .getAsJsonObject().getAsJsonObject("content").getAsJsonArray("parts").get(0)
                .getAsJsonObject().get("text").getAsString();
            return json.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message")
                .get("content").getAsString();
        }
        finally { pending.cancel(true); }
    }

    @Override public synchronized void close() { closed = true; executor.shutdownNow(); }
}
