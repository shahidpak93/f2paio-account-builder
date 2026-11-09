package com.example.f2paio;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
/** Own only the pacing overrides applied by this script; preserve all other settings. */
public final class AntiblockerTuner {
    private static final Map<String,Object> previous = new LinkedHashMap<>();
    private static final Map<String,Object> overrides = new LinkedHashMap<>();
    private AntiblockerTuner() { }
    public static synchronized void apply() {
        if (!previous.isEmpty()) return;
        set("takeMicroBreaks",false); set("microBreakChance",0.0); set("actionCooldownChance",0.02);
        DebugLog.event("PACING", "AIO scheduler owns micro pacing; manual pauses and external scheduled breaks remain respected");
    }
    private static void set(String name,Object value) {
        try {
            var field=Rs2AntibanSettings.class.getField(name);
            previous.put(name,field.get(null)); field.set(null,value); overrides.put(name,field.get(null));
        } catch (ReflectiveOperationException | IllegalArgumentException ex) { DebugLog.error(ex); }
    }
    public static synchronized void restore() {
        previous.forEach((name,value) -> {
            try {
                var field=Rs2AntibanSettings.class.getField(name);
                if (java.util.Objects.equals(field.get(null),overrides.get(name))) field.set(null,value);
            } catch (ReflectiveOperationException ex) { DebugLog.error(ex); }
        });
        previous.clear(); overrides.clear();
    }
}
