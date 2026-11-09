package com.example.f2paio;

import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperPlugin;

public final class QuestIntegration
{
    private final AtomicBoolean starting = new AtomicBoolean();
    private volatile String ownedQuest;
    private String priorTurnOn;
    private String priorObtain;
    private boolean configurationOwned;
    private boolean pausedForSupplies;
    private int rearrms;
    private long rearmWindowAt;

    public boolean step(Method method)
    {
        var plugin = Microbot.getPlugin(QuestHelperPlugin.class);
        if (plugin == null) { DebugLog.event("INTEGRATION", "Built-in Quest Helper plugin is unavailable"); return false; }
        if (!Microbot.getPluginManager().isActive(plugin))
        {
            if (starting.compareAndSet(false, true)) SwingUtilities.invokeLater(() -> {
                try
                {
                    Microbot.getPluginManager().setPluginEnabled(plugin, true);
                    Microbot.getPluginManager().startPlugin(plugin);
                }
                catch (Exception ex) { DebugLog.error(ex); }
                finally { starting.set(false); }
            });
            return true;
        }
        if (method.label.equals(ownedQuest))
        {
            // QuestScript stops itself and writes TurnOn=false when it hits a condition it will not
            // resolve; leaving it off means the helper silently idles forever. Re-arm a bounded
            // number of times, then let the engine watchdog stall out and defer the quest.
            if (!helperEnabled())
            {
                long now = System.currentTimeMillis();
                if (now - rearmWindowAt > 120_000) { rearrms = 0; rearmWindowAt = now; }
                if (++rearrms > 3)
                {
                    DebugLog.event("QUEST_HELPER_STUCK", method.label + ": QuestScript keeps stopping; deferring");
                    return false;
                }
                Microbot.getConfigManager().setRSProfileConfiguration("questhelper", "TurnOn", true);
                DebugLog.event("QUEST_HELPER_REARM", method.label + " TurnOn was disabled by QuestScript; re-armed " + rearrms + "/3");
            }
            return true;
        }
        if (!configurationOwned)
        {
            priorTurnOn = Microbot.getConfigManager().getRSProfileConfiguration("questhelper", "TurnOn");
            priorObtain = Microbot.getConfigManager().getRSProfileConfiguration("questhelper", "obtainMissingItems");
            configurationOwned = true;
        }
        Microbot.getConfigManager().setRSProfileConfiguration("questhelper", "TurnOn", true);
        boolean started = Microbot.getClientThread().runOnClientThreadOptional(
            () -> plugin.startQuestHelper(method.label)).orElse(false);
        if (started)
        {
            ownedQuest = method.label;
            rearrms = 0; rearmWindowAt = System.currentTimeMillis();
            Microbot.getConfigManager().setRSProfileConfiguration("questhelper", "obtainMissingItems", "YES");
            Microbot.getConfigManager().setRSProfileConfiguration("questhelper", "TurnOn", true);
            DebugLog.event("DELEGATE", "Quest Helper / QuestScript owns execution of " + method.label);
        }
        return started;
    }

    public java.util.List<ItemNeed> missingTradeables(GameState state)
    {
        var plugin = Microbot.getPlugin(QuestHelperPlugin.class);
        if (plugin == null || ownedQuest == null) return java.util.List.of();
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            java.util.List<ItemNeed> result = new java.util.ArrayList<>();
            var selected = plugin.getSelectedQuest();
            if (selected == null) return result;
            // Universal: use the helper's own quest-level item requirements plus the active step,
            // instead of a hand-maintained per-quest list.
            java.util.List<net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement> items =
                new java.util.ArrayList<>(selected.getItemRequirements());
            if (selected.getCurrentStep() != null)
            {
                var step = selected.getCurrentStep().getActiveStep();
                if (step instanceof net.runelite.client.plugins.microbot.questhelper.steps.DetailedQuestStep)
                {
                    for (var requirement : ((net.runelite.client.plugins.microbot.questhelper.steps.DetailedQuestStep) step).getRequirements())
                        if (requirement instanceof net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement)
                            items.add((net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement) requirement);
                }
            }
            java.util.Set<Integer> seen = new java.util.HashSet<>();
            for (var item : items)
            {
                int required = Math.max(1, item.getQuantity());
                int owned = java.util.stream.Stream.concat(state.items.stream(), state.equipment.stream())
                    .filter(i -> item.getAllIds().contains(i.id)).mapToInt(i -> i.quantity).sum();
                if (owned >= required) continue;
                for (int id : item.getAllIds())
                {
                    var composition = Microbot.getClient().getItemDefinition(id);
                    if (composition != null && composition.isTradeable() && seen.add(id))
                    {
                        result.add(new ItemNeed(id, composition.getName(), id == 995 ? required : required - owned));
                        break;
                    }
                }
            }
            return result;
        }).orElse(java.util.List.of());
    }

    public void pauseForSupplies()
    {
        if (!pausedForSupplies)
        {
            pausedForSupplies = true;
            Microbot.getConfigManager().setRSProfileConfiguration("questhelper", "TurnOn", false);
        }
    }

    public void resumeOwned()
    {
        if (ownedQuest != null && pausedForSupplies)
        {
            pausedForSupplies = false;
            Microbot.getConfigManager().setRSProfileConfiguration("questhelper", "TurnOn", true);
        }
    }

    /** Re-arm the helper after QuestScript stopped itself, or clear a stuck dialogue. */
    public void recover(GameState state)
    {
        try
        {
            if (Rs2Dialogue.isInDialogue()) { Rs2Dialogue.clickContinue(); DebugLog.event("QUEST_RECOVER", "dialogue continue"); return; }
            if (ownedQuest == null) return;
            Microbot.getConfigManager().setRSProfileConfiguration("questhelper", "TurnOn", true);
            var plugin = Microbot.getPlugin(QuestHelperPlugin.class);
            if (plugin == null) return;
            if (!Microbot.getPluginManager().isActive(plugin))
            {
                SwingUtilities.invokeLater(() -> {
                    try { Microbot.getPluginManager().setPluginEnabled(plugin, true); Microbot.getPluginManager().startPlugin(plugin); }
                    catch (Exception ex) { DebugLog.error(ex); }
                });
                return;
            }
            Microbot.getClientThread().runOnClientThreadOptional(() -> plugin.startQuestHelper(ownedQuest));
            DebugLog.event("QUEST_RECOVER", "re-selected " + ownedQuest + " and re-armed TurnOn");
        }
        catch (Exception ex) { DebugLog.event("QUEST_RECOVER_FAIL", ex.getClass().getSimpleName()); }
    }

    private boolean helperEnabled()
    {
        String value = Microbot.getConfigManager().getRSProfileConfiguration("questhelper", "TurnOn");
        return value == null || !"false".equalsIgnoreCase(value);
    }

    public void stopOwned()
    {
        if (ownedQuest == null) return;
        var plugin = Microbot.getPlugin(QuestHelperPlugin.class);
        String expected = ownedQuest; ownedQuest = null; pausedForSupplies = false;
        Microbot.getConfigManager().setRSProfileConfiguration("questhelper", "TurnOn", false);
        if (plugin != null) Microbot.getClientThread().invokeLater(() -> {
            var selected = plugin.getSelectedQuest();
            if (selected != null && selected.getQuest() != null && expected.equals(selected.getQuest().getName()))
                plugin.getQuestManager().shutDownQuest(true);
        });
    }

    public void close()
    {
        stopOwned();
        if (!configurationOwned) return;
        restore("TurnOn", priorTurnOn); restore("obtainMissingItems", priorObtain);
        configurationOwned = false;
    }

    private void restore(String key, String value)
    {
        if (value == null) Microbot.getConfigManager().unsetRSProfileConfiguration("questhelper", key);
        else Microbot.getConfigManager().setRSProfileConfiguration("questhelper", key, value);
    }
}
