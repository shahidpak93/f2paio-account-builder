package com.example.f2paio;
import java.nio.file.*;
import java.util.*;
/** Durable offer records for restart reconciliation; never adopts a stranger's offer. */
public final class OfferJournal {
    private static Path file;
    private static Map<String,Map<String,Object>> records=new LinkedHashMap<>();
    public static synchronized void account(String hash) {
        file=FilesStore.ROOT.resolve("learning").resolve(hash+"-offers.json");records=new LinkedHashMap<>();
        if(Files.exists(file)) try {
            var type=new com.google.gson.reflect.TypeToken<Map<String,Map<String,Object>>>(){}.getType();
            var loaded=FilesStore.JSON.<Map<String,Map<String,Object>>>fromJson(Files.readString(file),type);
            if(loaded!=null) records=loaded;
        } catch(Exception ex) {DebugLog.event("GE_RECONCILE","Invalid journal ignored");}
        for(var entry:records.entrySet()) DebugLog.event("GE_RECONCILE","Recorded offer "+entry.getKey()+"; existing native offers remain protected; inspect current inventory/slot before new submission");
    }
    public static synchronized void submitted(String slot,int id,String name,int quantity,int price,boolean sell) {
        records.put(slot,new LinkedHashMap<>(Map.of("itemId",id,"name",name,"quantity",quantity,"price",price,"sell",sell,"at",System.currentTimeMillis(),"state","SUBMITTED")));save();
    }
    public static synchronized void state(String slot,String state) {
        if(records.containsKey(slot)) {records.get(slot).put("state",state);save();}
    }
    private static void save() {try {if(file!=null) FilesStore.write(file,records);}catch(Exception ex){DebugLog.event("GE_JOURNAL",ex.getClass().getSimpleName());}}
    private OfferJournal(){}
}
