package com.example.f2paio;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
/** Independent incident channel; never occupies the routine planner's in-flight slot. */
public final class AgentSupervisor implements AutoCloseable {
    private final IncidentDetector detector=new IncidentDetector();
    private final ExecutorService worker=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"F2PAIO-supervisor");t.setDaemon(true);return t;});
    private CompletableFuture<String> pending;
    private volatile String status="Supervisor watching";
    private long reportAt;
    private String forcedCause;
    public void failed(Method method,GameState state,long now,String phase,String reason,List<ResourceItem> bank,boolean enabled) {
        forcedCause=reason;step(method,state,now,phase,reason,bank,enabled);
    }
    private CompletableFuture<Boolean> selection;
    public void selection(Method method,GameState state,List<ResourceItem> bank,boolean enabled) {
        if(selection!=null) selection.cancel(true);
        if(!enabled) {selection=null;return;}
        var payload=new LinkedHashMap<String,Object>();payload.put("goal",method.id);
        payload.put("state",Map.of("levels",state.levels,"affordable",BuilderBudget.affordable(method,state,bank,id -> TradeService.unitPrice(id,true))));
        String body=FilesStore.JSON.toJson(payload);
        selection=CompletableFuture.supplyAsync(()-> {
            try {
                var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:8092/review-selection"))
                    .timeout(Duration.ofSeconds(4)).header("Content-Type","application/json")
                    .header("X-Osrs-Agent-Token",Files.readString(FilesStore.ROOT.resolve("agent-lab-token")).trim())
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
                var result=HttpClient.newHttpClient().send(req,HttpResponse.BodyHandlers.ofString());
                if(result.statusCode()!=200) throw new IllegalStateException("Verifier HTTP "+result.statusCode());
                return new com.google.gson.JsonParser().parse(result.body()).getAsJsonObject().get("allowed").getAsBoolean();
            } catch(Exception ex) {throw new CompletionException(ex);}
        },worker);
        status="Verifier reviewing "+method.id;DebugLog.event("AGENT_CONTACT",status);
    }
    public String preflight() {
        if(selection==null) return "READY";
        if(!selection.isDone()) return "WAIT";
        try {
            boolean allowed=selection.join();status="Verifier "+(allowed?"accepted":"rejected")+" selected goal";
            DebugLog.event("AGENT_PREFLIGHT",status);return allowed?"READY":"REJECT";
        } catch(Exception ex) {
            status="Verifier unavailable; Java level/budget contracts remain active";
            DebugLog.event("AGENT_PREFLIGHT",status);return "READY";
        } finally {selection=null;}
    }

    public String status() {return status;}
    public void pause(long now) {detector.pause(now);}
    public boolean step(Method method,GameState s,long now,String phase,String detail,List<ResourceItem> bank,boolean enabled) {
        if(pending!=null && pending.isDone()) {
            try {status="Agent team reviewed incident: "+pending.join();DebugLog.event("AGENT_REVIEW",status);}
            catch(Exception ex) {status="Agent team unavailable; deterministic replan retained";DebugLog.event("AGENT_REVIEW",status);}
            pending=null;
        }
        if(method==null) {detector.reset();return false;}
        String cause=forcedCause!=null?forcedCause:detector.observe(method.id,s,now);forcedCause=null;
        if(cause==null) return false;
        status="Incident: "+cause;DebugLog.event("SUPERVISOR_INCIDENT",method.id+" phase="+phase+" "+cause);
        if(enabled && pending==null && now-reportAt>=60_000) {
            reportAt=now;
            var snapshot=new LinkedHashMap<String,Object>();
            snapshot.put("pluginVersion",Tuning.VERSION);snapshot.put("incident",UUID.randomUUID().toString());snapshot.put("cause",cause);snapshot.put("goal",method.id);
            snapshot.put("phase",phase);snapshot.put("status",detail);snapshot.put("position",s.location.toString());
            snapshot.put("xp",s.xp);snapshot.put("coins",s.coins);snapshot.put("levels",s.levels);snapshot.put("quests",s.quests);
            snapshot.put("items",s.items);snapshot.put("bank",bank);snapshot.put("allowedActions",List.of("REPLAN","DEFER"));
            snapshot.put("logs",DebugLog.recent().stream().skip(Math.max(0,DebugLog.recent().size()-16)).collect(java.util.stream.Collectors.toList()));
            String json=FilesStore.JSON.toJson(snapshot);
            pending=CompletableFuture.supplyAsync(()->request(json),worker);
            DebugLog.event("AGENT_CONTACT","Incident sent to verifier/researcher/planner workflow");
        }
        detector.reset();return true;
    }
    private String request(String json) {
        try {
            var token=FilesStore.ROOT.resolve("agent-lab-token");
            HttpRequest request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:8092/supervise"))
                .timeout(Duration.ofSeconds(110)).header("Content-Type","application/json")
                .header("X-Osrs-Agent-Token",Files.readString(token).trim()).POST(HttpRequest.BodyPublishers.ofString(json)).build();
            var result=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build().send(request,HttpResponse.BodyHandlers.ofString());
            if(result.statusCode()!=200) throw new IllegalStateException("HTTP "+result.statusCode());
            var body=new com.google.gson.JsonParser().parse(result.body()).getAsJsonObject();
            return body.get("action").getAsString()+"; report="+body.get("incident").getAsString();
        } catch(Exception ex) {throw new CompletionException(ex);}
    }
    public void close() {worker.shutdownNow();}
}
