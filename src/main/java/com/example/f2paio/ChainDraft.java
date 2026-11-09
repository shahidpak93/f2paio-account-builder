package com.example.f2paio;
import com.google.gson.JsonObject;
/** Compile a short capability sequence into the full executor/verifier contract. */
public final class ChainDraft {
    public static ChainPlan compile(JsonObject json) {
        try {
            if(json==null) return null;
            if(json.has("steps")) {
                ChainPlan p=FilesStore.JSON.fromJson(json,ChainPlan.class); return ChainPlan.valid(p)?p:null;
            }
            String goal=json.get("goal").getAsString(); int batch=json.has("batch")?json.get("batch").getAsInt():4;
            var methods=json.getAsJsonArray("methods");
            if(methods==null || methods.size()<1 || methods.size()>3 || batch<1 || batch>8 || batch*methods.size()>24) return null;
            ChainPlan plan=new ChainPlan(); plan.goal=goal; plan.origin="ai-proposed";
            StringBuilder identity=new StringBuilder(goal);
            for(var entry:methods) {
                String id=entry.getAsString(); Method method=Methods.get(id);
                if(!ChainPlan.supported(method)) return null;
                identity.append(":").append(id);
                plan.addBlock(method,batch);
            }
            if(!methods.get(methods.size()-1).getAsString().equals(goal)) return null;
            plan.id="chain_ai_"+goal+"_"+Integer.toUnsignedString(identity.toString().hashCode(),16);
            plan.steps.add(new ChainPlan.Step(Methods.get(goal).kind==Method.Kind.GROUND?ChainPlan.Op.SELL:ChainPlan.Op.BANK,goal,1));
            return ChainPlan.valid(plan)?plan:null;
        } catch(RuntimeException ex) { return null; }
    }
    private ChainDraft() { }
}
