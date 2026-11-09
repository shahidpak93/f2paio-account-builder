package com.example.f2paio;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Retrieval keeps game facts inspectable and refreshable rather than assuming model memory is current. */
public final class KnowledgeRules {
    public static final class Rule { public String id,topic,tip,source,scope; public long revision; }
    private static final List<Rule> RULES=load();
    private static List<Rule> load() {
        try(InputStream input=KnowledgeRules.class.getResourceAsStream("/osrs-knowledge.json")) {
            if(input==null) return List.of();
            Rule[] data=FilesStore.JSON.fromJson(new String(input.readAllBytes(),StandardCharsets.UTF_8),Rule[].class);
            return data==null?List.of():List.of(data);
        } catch(Exception ex) { return List.of(); }
    }
    public static List<Rule> search(String query,int limit) {
        Set<String> words=new HashSet<>(List.of(query.toLowerCase(Locale.ROOT).replace('_',' ').split("[^a-z0-9]+")));
        words.removeIf(w->w.length()<3);
        Map<Rule,Integer> scores=new HashMap<>();
        for(Rule rule:RULES) {
            String content=(rule.topic+" "+rule.tip).toLowerCase(Locale.ROOT);
            int score=words.stream().mapToInt(w->content.contains(w)?1:0).sum();
            if(score>0) scores.put(rule,score);
        }
        return scores.keySet().stream().sorted(Comparator.<Rule>comparingInt(scores::get).reversed().thenComparing(r->r.id))
            .limit(Math.max(0,Math.min(12,limit))).collect(java.util.stream.Collectors.toList());
    }
    public static String context(String query,int limit) {
        StringBuilder text=new StringBuilder();
        for(Rule rule:search(query,limit)) text.append(rule.id).append(": ").append(rule.tip).append(" [").append(rule.source).append("]\n");
        return text.toString();
    }
    public static int size() { return RULES.size(); }
    private KnowledgeRules() { }
}
