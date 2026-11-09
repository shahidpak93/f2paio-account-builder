package com.example.f2paio;
import java.util.*;
/** Typed plan over registered capabilities. Every action has an explicit effect verifier. */
public final class ChainPlan {
    public enum Op { PREPARE, TRAVEL, ACT, VERIFY, BANK, SELL }
    public static final class Step {
        public Op op; public String method; public int count=1;
        public String output; public int minimum;
        public Step() { }
        public Step(Op op,String method,int count) { this.op=op; this.method=method; this.count=count; }
    }
    public String id,goal,parent="",origin="synthesized";
    public int generation,verifySeconds=30;
    public List<Step> steps=new ArrayList<>();
    public static String contentId(ChainPlan p) {
        StringBuilder data=new StringBuilder(p.goal+":"+p.verifySeconds);
        for(Step step:p.steps) {
            data.append('|').append(step.op).append(':').append(step.method).append(':').append(step.count);
            if (step.output!=null) data.append(':').append(step.output).append(':').append(step.minimum);
        }
        try {
            byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder();for(int i=0;i<6;i++) hex.append(String.format("%02x",hash[i]));
            return "chain_"+p.goal+"_agent_"+hex;
        } catch(java.security.NoSuchAlgorithmException ex) {throw new IllegalStateException(ex);}
    }
    public static ChainPlan seed(Method method) {
        ChainPlan p=new ChainPlan(); p.id="chain_"+method.id; p.goal=method.id;
        p.addBlock(method,24);
        p.steps.add(new Step(method.kind==Method.Kind.GROUND?Op.SELL:Op.BANK,method.id,1));
        return p;
    }
    void addBlock(Method method,int batch) { addBlock(method,batch,null); }

    void addBlock(Method method,int batch,ItemNeed output) {
        steps.add(new Step(Op.PREPARE,method.id,1));
        steps.add(new Step(Op.TRAVEL,method.id,1));
        Step act = new Step(Op.ACT,method.id,batch);
        if (output!=null) { act.output=output.name; act.minimum=output.quantity; }
        steps.add(act);
        steps.add(new Step(Op.VERIFY,method.id,1));
    }
    public static boolean valid(ChainPlan p) {
        if(p==null || p.id==null || !p.id.matches("chain_[a-z0-9_]{1,100}") || p.goal==null
            || p.steps==null || p.steps.size()<4 || p.steps.size()>17 || p.verifySeconds<15 || p.verifySeconds>60) return false;
        try {
            Method goal=Methods.get(p.goal);
            if(!supported(goal)) return false;
            boolean goalVerified=false; int totalActions=0;
            for(int i=0;i<p.steps.size();i++) {
                Step s=p.steps.get(i);
                if(s==null || s.op==null || s.method==null || !supported(Methods.get(s.method))) return false;
                if(s.op!=Op.ACT && s.count!=1) return false;
                if (s.output!=null && (s.op!=Op.ACT || s.minimum<1 || s.minimum>24
                    || !Methods.get(s.method).products.contains(s.output))) return false;
                if (s.output==null && s.minimum!=0) return false;
                switch(s.op) {
                    case PREPARE:
                        if(i+3>=p.steps.size() || !is(p.steps.get(i+1),Op.TRAVEL,s.method)
                            || !is(p.steps.get(i+2),Op.ACT,s.method) || !is(p.steps.get(i+3),Op.VERIFY,s.method)) return false;
                        break;
                    case TRAVEL: if(i<1 || !is(p.steps.get(i-1),Op.PREPARE,s.method)) return false; break;
                    case ACT:
                        if(i<2 || !is(p.steps.get(i-1),Op.TRAVEL,s.method) || s.count<1 || s.count>24) return false;
                        totalActions+=s.count; break;
                    case VERIFY:
                        if(i<1 || !is(p.steps.get(i-1),Op.ACT,s.method)) return false;
                        if(s.method.equals(p.goal)) goalVerified=true; break;
                    case BANK: case SELL:
                        if(i!=p.steps.size()-1 || !goalVerified || !s.method.equals(p.goal)) return false;
                        break;
                    default: return false;
                }
            }
            int limit = p.steps.stream().anyMatch(s -> s.output!=null) ? 60 : 24;
            return goalVerified && totalActions<=limit;
        } catch(RuntimeException ex) { return false; }
    }
    private static boolean is(Step s,Op op,String id) { return s!=null && s.op==op && id.equals(s.method); }
    static boolean supported(Method m) { return m.kind==Method.Kind.OBJECT || m.kind==Method.Kind.FISHING
        || m.kind==Method.Kind.GROUND || m.kind==Method.Kind.PROCESS; }
    public ChainPlan copy() { return FilesStore.JSON.fromJson(FilesStore.JSON.toJson(this),ChainPlan.class); }
}
