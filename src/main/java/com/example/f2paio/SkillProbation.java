package com.example.f2paio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Declarative proposals need observed effects before promotion; no generated Java is executed. */
public final class SkillProbation {
    public static final class Evidence {
        public int confirmed, rejected;
        public String status = "PROBATION";
    }
    private final Path file;
    private final Map<String,Evidence> evidence = new HashMap<>();
    public SkillProbation(Path file) {
        this.file=file;
        if (Files.exists(file)) try {
            var type=new com.google.gson.reflect.TypeToken<Map<String,Evidence>>(){}.getType();
            Map<String,Evidence> loaded=FilesStore.JSON.fromJson(Files.readString(file),type);
            if(loaded!=null) loaded.forEach((id,e)-> {if(e!=null && e.confirmed>=0 && e.rejected>=0) evidence.put(id,e);});
        } catch(Exception ex) { DebugLog.event("SKILL_HISTORY", "Invalid probation history ignored"); }
    }
    public synchronized String status(String id) { return evidence.computeIfAbsent(id,k->new Evidence()).status; }
    public synchronized void observe(String id,boolean confirmed) {
        Evidence e=evidence.computeIfAbsent(id,k->new Evidence());
        if(confirmed) e.confirmed++; else e.rejected++;
        if(e.rejected>=2) e.status="QUARANTINED";
        else if(e.confirmed>=3) e.status="PROMOTED";
        try { FilesStore.write(file,evidence); } catch(Exception ex) { DebugLog.error(ex); }
        DebugLog.event("SKILL_EVIDENCE",id+" status="+e.status+" confirmed="+e.confirmed+" rejected="+e.rejected);
    }
    public static boolean valid(Method m) {
        if(m==null || m.id==null || !m.id.matches("learned_[a-z0-9_]{1,60}")
            || m.label==null || m.label.isBlank() || m.label.length()>120 || m.level<1 || m.level>99
            || m.skill==net.runelite.api.Skill.OVERALL || m.location==null || m.location.getPlane()!=0
            || m.location.getX()<1 || m.location.getX()>16383 || m.location.getY()<1 || m.location.getY()>16383
            || m.names.length==0 || m.names.length>12 || m.products.isEmpty() || m.products.size()>12) return false;
        if(m.kind!=Method.Kind.OBJECT && m.kind!=Method.Kind.FISHING && m.kind!=Method.Kind.GROUND) return false;
        if(m.action==null || !java.util.Set.of("Chop down","Mine","Net","Bait","Lure","Cage","Harpoon","Take").contains(m.action)) return false;
        if(m.kind==Method.Kind.GROUND && !m.action.equals("Take")) return false;
        if(m.kind==Method.Kind.OBJECT && !(m.action.equals("Mine") || m.action.equals("Chop down"))) return false;
        if(m.kind==Method.Kind.OBJECT && !m.tools.contains(m.action.equals("Mine")?"pickaxe":"axe")) return false;
        if(m.kind==Method.Kind.FISHING && (!java.util.Set.of("Net","Bait","Lure","Cage","Harpoon").contains(m.action)
            || m.tools.isEmpty())) return false;
        for(String name:m.names) if(name==null || name.isBlank() || name.length()>80) return false;
        for(String product:m.products) if(product==null || product.isBlank() || product.length()>80) return false;
        return m.tools.stream().allMatch(tool->java.util.Set.of("axe","pickaxe","Small fishing net","Fly fishing rod",
            "Fishing rod","Feather","Fishing bait","Lobster pot","Harpoon").contains(tool));
    }
}
