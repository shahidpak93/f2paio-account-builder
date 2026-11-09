package com.example.f2paio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * Bridge to the local agent gateway (127.0.0.1:8092).
 *
 * <p>Chat/monitor are advisory: the gateway can request only a small whitelist of runtime actions,
 * and the Java controller executes them. The gateway never sends executable code into this process.
 * Growth proposals are declarative skills/chains and go through the existing typed validators.</p>
 */
public final class AiBridge
{
    public static final Set<String> SAFE_ACTIONS = Set.of("NONE", "RESUME", "SKIP_GOAL",
        "CLOSE_INTERFACE", "BANK_LEFTOVERS", "RESTART_QUEST", "REPLAN");
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    public static final class Reply
    {
        public final String reply, action, reason, model, error;
        Reply(String reply, String action, String reason, String model, String error)
        { this.reply = reply; this.action = action; this.reason = reason; this.model = model; this.error = error; }
    }

    public static final class Monitor
    {
        public final String severity, action, note;
        Monitor(String severity, String action, String note) { this.severity = severity; this.action = action; this.note = note; }
    }

    public static final class Proposal
    {
        public String type, id, label, skill, action, rationale, model;
        public String[] names = new String[0], products = new String[0], tools = new String[0];
        public int level = 1;
        public String worldX, worldY, worldPlane;
        public double confidence;
        public boolean valid;
        public String invalidReason = "";
    }

    public static boolean allowed(String action)
    {
        return action != null && SAFE_ACTIONS.contains(action.toUpperCase(Locale.ROOT));
    }

    public static Reply chat(String message, String context)
    {
        try
        {
            JsonObject body = new JsonObject();
            body.addProperty("message", message);
            body.add("context", new JsonParser().parse(context == null || context.isBlank() ? "{}" : context));
            JsonObject result = post("/chat", body);
            String action = text(result, "action").toUpperCase(Locale.ROOT);
            if (!allowed(action)) action = "NONE";
            return new Reply(text(result, "reply"), action, text(result, "reason"), text(result, "model"), result.has("error") && !result.get("error").isJsonNull()?text(result,"error"):null);
        }
        catch (Exception ex)
        {
            return new Reply("Agent gateway unavailable: " + ex.getClass().getSimpleName()
                + ". Check StartAgents.ps1 and the local model.", "NONE", "gateway_error", "none", ex.getClass().getSimpleName());
        }
    }

    public static Monitor monitor(String context)
    {
        try
        {
            JsonObject body = new JsonObject();
            body.add("context", new JsonParser().parse(context == null || context.isBlank() ? "{}" : context));
            JsonObject result = post("/monitor", body);
            String action = text(result, "action").toUpperCase(Locale.ROOT);
            if (!allowed(action)) action = "NONE";
            return new Monitor(text(result, "severity"), action, text(result, "note"));
        }
        catch (Exception ex)
        {
            return new Monitor("info", "NONE", "gateway unavailable: " + ex.getClass().getSimpleName());
        }
    }

    public static JsonObject evolve(String learningJson)
    {
        try
        {
            JsonObject body = new JsonObject();
            body.add("learning", new JsonParser().parse(learningJson == null || learningJson.isBlank() ? "{}" : learningJson));
            return post("/evolve", body);
        }
        catch (Exception ex)
        {
            DebugLog.event("GROWTH_ERROR", ex.getClass().getSimpleName());
            return null;
        }
    }

    public static boolean feedback(String proposalId, String status, String evidence)
    {
        try
        {
            JsonObject body = new JsonObject();
            body.addProperty("proposalId", proposalId);
            body.addProperty("status", status);
            body.addProperty("evidence", evidence == null ? "" : evidence);
            post("/evolve/feedback", body);
            DebugLog.event("GROWTH_FEEDBACK", proposalId + " " + status);
            return true;
        }
        catch (Exception ex) { DebugLog.event("GROWTH_FEEDBACK_ERROR", ex.getClass().getSimpleName()); return false; }
    }

    public static JsonObject jobs() {
        try {return post("/developer/status",new JsonObject());}
        catch(Exception ex) {JsonObject value=new JsonObject();value.addProperty("error",ex.getClass().getSimpleName());return value;}
    }

    private static JsonObject post(String path, JsonObject body) throws Exception
    {
        String token = Files.readString(FilesStore.ROOT.resolve("agent-lab-token")).trim();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:8092" + path))
            .timeout(TIMEOUT).header("Content-Type", "application/json")
            .header("X-Osrs-Agent-Token", token)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .build().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
        return new JsonParser().parse(response.body()).getAsJsonObject();
    }

    private static String text(JsonObject object, String key)
    {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private AiBridge() { }
}
