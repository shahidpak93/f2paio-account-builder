package com.example.f2paio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Growth loop: report learning state to the agent team, receive one declarative skill/chain,
 * validate it with the existing typed gates, run it, then report the verified outcome back.
 *
 * <p>This is the "AI proposes -> bot uses -> bot reports -> agents evolve" cycle. The agent never
 * sends executable Java into this process; skills are declarative methods and chains are typed
 * plans, both validated by the same classes the controller already trusts.</p>
 */
public final class GrowthLoop
{
    private final Path stateFile;
    private final Map<String, String> pending = new LinkedHashMap<>(); // proposalId -> "skill:id" / "chain:id"
    private long nextAt;
    private volatile boolean inFlight;
    private volatile boolean closed;
    private final java.util.Set<String> feedbackInFlight=new java.util.HashSet<>();
    private final java.util.Map<String,String> reportedSignature=new java.util.HashMap<>();
    private final java.util.Map<String,Long> feedbackRetry=new java.util.HashMap<>();
    public void close(){closed=true;}


    public GrowthLoop(Path stateFile)
    {
        this.stateFile = stateFile;
        try
        {
            if (stateFile != null && java.nio.file.Files.exists(stateFile))
            {
                var type = new com.google.gson.reflect.TypeToken<Map<String, String>>() { }.getType();
                Map<String, String> loaded = FilesStore.JSON.fromJson(java.nio.file.Files.readString(stateFile), type);
                if (loaded != null) pending.putAll(loaded);
            }
        }
        catch (Exception ex) { DebugLog.event("GROWTH_LOAD", "Invalid growth history ignored"); }
    }

    public synchronized Map<String, String> pending() { return new LinkedHashMap<>(pending); }

    public void tick(long now, GameState state, ChainLibrary library)
    {
        if (closed || library == null || state == null) return;
        reportOutcomes(library);
        if (inFlight || now < nextAt) return;
        nextAt = now + (library.summary().contains("0 proven")?5:30) * Tuning.MINUTE;
        inFlight = true;
        var learning = new LinkedHashMap<String, Object>();
        learning.put("chainSummary", library.summary());
        learning.put("chains", library.details());
        var skills = new java.util.ArrayList<String>();
        for (String id : Methods.learnedIds()) skills.add(id + " status=" + Methods.learnedStatus(id));
        learning.put("learnedSkills", skills);
        learning.put("levels", state.levels);
        learning.put("questsFinished", state.quests.values().stream().filter("FINISHED"::equals).count());
        learning.put("goal", "account building; fix probation/quarantined goals first");
        final String json = FilesStore.JSON.toJson(learning);
        CompletableFuture.supplyAsync(() -> AiBridge.evolve(json)).whenComplete((result, error) ->
        {
            inFlight = false;
            if(closed) return;
            if (result == null) { DebugLog.event("GROWTH_NONE", "agent gateway unavailable or no proposal"); return; }
            apply(result, library);
        });
    }

    private synchronized void apply(JsonObject result, ChainLibrary library)
    {
        if(closed) return;
        try
        {
            String type = result.has("type") ? result.get("type").getAsString() : "none";
            if (!"skill".equals(type) && !"chain".equals(type))
            {
                DebugLog.event("GROWTH_NONE", "agents proposed nothing: "
                    + (result.has("reason") ? result.get("reason").getAsString() : "no reason"));
                return;
            }
            String proposalId = result.has("id") ? result.get("id").getAsString() : "";
            JsonObject proposal = result.getAsJsonObject("proposal");
            if (proposal == null || proposalId.isBlank()) return;
            if ("skill".equals(type))
            {
                JsonObject skill = proposal.getAsJsonObject("skill");
                Method method = parseSkill(skill);
                if (method == null || !SkillProbation.valid(method))
                {
                    DebugLog.event("GROWTH_REJECTED", "skill failed typed validation");
                    AiBridge.feedback(proposalId, "REJECTED", "typed validation failed");
                    return;
                }
                Methods.saveLearnedSkill(method);
                pending.put(proposalId, "skill:" + method.id);
                DebugLog.event("GROWTH_SKILL", proposalId + " -> " + method.id + " (" + method.label + ")"
                    + "; probation until verified");
            }
            else
            {
                JsonObject chain = proposal.getAsJsonObject("chain");
                ChainPlan plan = parseChain(chain);
                if (plan == null || !ChainPlan.valid(plan))
                {
                    DebugLog.event("GROWTH_REJECTED", "chain failed typed validation");
                    AiBridge.feedback(proposalId, "REJECTED", "chain validation failed");
                    return;
                }
                if (!library.propose(plan))
                {
                    DebugLog.event("GROWTH_REJECTED", "chain rejected by library (duplicate or goal limit)");
                    AiBridge.feedback(proposalId, "REJECTED", "library rejected plan");
                    return;
                }
                pending.put(proposalId, "chain:" + plan.id);
                DebugLog.event("GROWTH_CHAIN", proposalId + " -> " + plan.id + " on probation");
            }
            save();
        }
        catch (Exception ex) { DebugLog.event("GROWTH_APPLY_FAIL", ex.getClass().getSimpleName()); }
    }

    private synchronized void reportOutcomes(ChainLibrary library)
    {
        long now=System.currentTimeMillis();
        for(var entry:new java.util.ArrayList<>(pending.entrySet())) {
            String id=entry.getKey();String[] target=entry.getValue().split(":",2);
            if(target.length!=2 || feedbackInFlight.contains(id) || now<feedbackRetry.getOrDefault(id,0L)) continue;
            String status;String evidence;
            if(target[0].equals("skill")) {status=Methods.learnedStatus(target[1]);evidence="skill "+target[1]+" "+status;}
            else {
                var value=library.evidence(target[1]);if(value==null) continue;
                status=value.status;evidence="chain "+target[1]+" successes="+value.success+" failures="+value.failure;
                if(status.equals("PROBATION")) status=value.failure>0?"FAILED":value.success>0?"CONFIRMED":"PROBATION";
            }
            if(status.equals("PROBATION") || evidence.equals(reportedSignature.get(id))) continue;
            final String sendStatus=status;final String sendEvidence=evidence;
            boolean terminal=status.equals("PROMOTED") || status.equals("QUARANTINED");
            feedbackInFlight.add(id);
            java.util.concurrent.CompletableFuture.supplyAsync(()->AiBridge.feedback(id,sendStatus,sendEvidence)).whenComplete((ack,error)-> {
                synchronized(GrowthLoop.this) {
                    feedbackInFlight.remove(id);
                    if(Boolean.TRUE.equals(ack) && error==null) {reportedSignature.put(id,sendEvidence);if(terminal) pending.remove(id);feedbackRetry.remove(id);save();}
                    else feedbackRetry.put(id,System.currentTimeMillis()+60_000);
                }
            });
        }
    }

    private Method parseSkill(JsonObject skill)
    {
        try
        {
            String id = skill.get("id").getAsString();
            String label = skill.has("label") ? skill.get("label").getAsString() : id;
            String skillName = skill.get("skill").getAsString();
            int level = skill.get("level").getAsInt();
            String kindName = skill.get("kind").getAsString();
            Method.Kind kind = Method.Kind.valueOf(kindName);
            String action = skill.get("action").getAsString();
            var location = skill.getAsJsonObject("location");
            var point = new net.runelite.api.coords.WorldPoint(location.get("x").getAsInt(),
                location.get("y").getAsInt(), location.has("plane") ? location.get("plane").getAsInt() : 0);
            String[] names = strings(skill.getAsJsonArray("names"));
            String[] products = strings(skill.getAsJsonArray("products"));
            String[] tools = strings(skill.getAsJsonArray("tools"));
            return new Method(id, label, kind, net.runelite.api.Skill.valueOf(skillName), level,
                names, action, point, java.util.List.of(products), java.util.List.of(tools));
        }
        catch (Exception ex) { return null; }
    }

    private ChainPlan parseChain(JsonObject chain)
    {
        try
        {
            ChainPlan plan = FilesStore.JSON.fromJson(chain, ChainPlan.class);
            if (plan == null) return null;
            plan.id = ChainPlan.contentId(plan);
            plan.origin = "agent";
            return plan;
        }
        catch (Exception ex) { return null; }
    }

    private static String[] strings(com.google.gson.JsonArray array)
    {
        if (array == null) return new String[0];
        String[] values = new String[array.size()];
        for (int i = 0; i < array.size(); i++) values[i] = array.get(i).getAsString();
        return values;
    }

    private void save()
    {
        try { if (stateFile != null) FilesStore.write(stateFile, pending); }
        catch (Exception ex) { DebugLog.event("GROWTH_SAVE", ex.getClass().getSimpleName()); }
    }
}
