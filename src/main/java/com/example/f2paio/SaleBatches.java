package com.example.f2paio;
import java.util.*;
/** Aggregate unstacked inventory slots before creating one GE offer per item ID. */
public final class SaleBatches {
    public static List<ResourceItem> group(List<ResourceItem> items,Set<String> allowed,Set<String> reserved) {
        Map<Integer,ResourceItem> grouped=new LinkedHashMap<>();
        for(ResourceItem item:items) {
            if(item.id==995 || item.quantity<=0 || !allowed.contains(item.name) || reserved.contains(item.name)) continue;
            int canonical=item.canonicalId();
            ResourceItem old=grouped.get(canonical);
            long count=(old==null?0L:old.quantity)+item.quantity;
            grouped.put(canonical,new ResourceItem(canonical,item.name,(int)Math.min(Integer.MAX_VALUE,count)));
        }
        return new ArrayList<>(grouped.values());
    }
    public static int physicalId(List<ResourceItem> items,int canonical) {
        return items.stream().filter(i->i.canonicalId()==canonical && i.quantity>0).mapToInt(i->i.id).findFirst().orElse(-1);
    }
    private SaleBatches() { }
}
