package com.example.f2paio;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

/** Source-attributed production data over the fixed native execution operations. */
final class F2pProduction {
    private static final Map<String,Spec> RECIPES = load();

    static final class Input {
        int id, quantity, minimum;
        String name;

        ItemNeed need() { return new ItemNeed(id,name,quantity); }
    }

    static final class Spec {
        String id,label,operation,source;
        Skill skill;
        int level,x,y,insideX,insideY,accessId,wearableId;
        String accessName;
        List<String> products;
        List<Input> inputs;

        boolean altar() { return "altar".equals(operation); }
        boolean inside(GameState state) {
            return altar() && state.location.distanceTo(new WorldPoint(insideX,insideY,0))<=30;
        }
        boolean equipped(GameState state) {
            return wearableId>0 && state.equipment.stream().anyMatch(item -> item.id==wearableId);
        }
    }

    static Spec get(String id) { return RECIPES.get(id); }
    static Iterable<Spec> all() { return RECIPES.values(); }

    static List<ItemNeed> inputs(String id) {
        Spec spec=get(id);
        return spec==null ? List.of() : spec.inputs.stream().map(Input::need).collect(java.util.stream.Collectors.toList());
    }

    static int minimum(String id,int itemId) {
        Spec spec=get(id);
        return spec==null ? 1 : spec.inputs.stream().filter(input -> input.id==itemId)
            .mapToInt(input -> input.minimum).findFirst().orElse(1);
    }

    private static Map<String,Spec> load() {
        Map<String,Spec> result=new LinkedHashMap<>();
        try (InputStream data=F2pProduction.class.getResourceAsStream("/f2p-production.json")) {
            if (data==null) throw new IllegalStateException("Production data missing");
            Spec[] rows=FilesStore.JSON.fromJson(new InputStreamReader(data,StandardCharsets.UTF_8),Spec[].class);
            for (Spec row:rows) {
                if (row.skill==null || row.level<1 || row.level>99 || row.products.isEmpty() || row.inputs.isEmpty()
                    || !java.util.Set.of("altar","cook","wine","leather","smelt","combine","anvil").contains(row.operation)
                    || row.inputs.stream().anyMatch(i -> i.id<1 || i.minimum<1 || i.quantity<i.minimum || i.quantity>27)
                    || result.putIfAbsent(row.id,row)!=null)
                    throw new IllegalStateException("Invalid production row "+row.id);
            }
        } catch (Exception error) {
            DebugLog.error(error);
            return Map.of();
        }
        return Collections.unmodifiableMap(result);
    }

    private F2pProduction() { }
}
