package com.example.f2paio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Knowledge about the account: what is unlocked, what has a one-time reward, and what is farmable.
 *
 * <p>Quest rewards and things like the Stronghold of Security reward are one-time per account and
 * must never be repeated. Ordinary resources (logs, ores, fish, drops) are farmable and may be
 * repeated. The one-time ledger is persisted per account so a restart cannot repeat a claim.</p>
 */
public final class AccountKnowledge
{
    /** A known one-time reward/opportunity. */
    public static final class OneTime
    {
        public final String id, label, unlock;
        public final long coins;
        public final List<String> items;
        public final boolean executable; // false = known opportunity, route not implemented yet

        OneTime(String id, String label, String unlock, long coins, List<String> items, boolean executable)
        {
            this.id = id; this.label = label; this.unlock = unlock; this.coins = coins;
            this.items = items; this.executable = executable;
        }
    }

    private static final List<OneTime> KNOWN = List.of(
        new OneTime("stronghold_of_security",
            "Stronghold of Security reward: 10,000 coins and a pair of boots",
            "Stronghold of Security", 10_000, List.of("Fancy boots", "Fighting boots", "Fancier boots"), false)
    );

    /** F2P unlocks a player recognises from the quest list. */
    private static final java.util.Map<String, String> UNLOCKS = java.util.Map.of(
        "Rune Mysteries", "Rune essence mine teleport",
        "The Corsair Curse", "Corsair Cove",
        "Below Ice Mountain", "Camdozaal",
        "X Marks the Spot", "X Marks the Spot quest completed",
        "Dragon Slayer I", "Rune platebody wear"
    );

    private final Path ledger;
    private final Set<String> claimed = new LinkedHashSet<>();
    private String view = "Account knowledge: scanning";

    public AccountKnowledge(Path ledger)
    {
        this.ledger = ledger;
        if (ledger != null && Files.exists(ledger))
        {
            try
            {
                String[] saved = FilesStore.read(ledger, String[].class);
                if (saved != null) for (String id : saved) if (id != null && !id.isBlank()) claimed.add(id);
            }
            catch (Exception ex) { DebugLog.event("KNOWLEDGE_LOAD", "Invalid one-time ledger ignored"); }
        }
    }

    /** Refresh derived state: owning a reward item counts as claimed even if the ledger is missing. */
    public void refresh(GameState state)
    {
        if (state == null) return;
        if(Boolean.TRUE.equals(state.rewardFlags.get("sos_flap"))) record("sos_floor1_coins");
        if(Boolean.TRUE.equals(state.rewardFlags.get("sos_doh"))) record("sos_floor2_coins");
        if(Boolean.TRUE.equals(state.rewardFlags.get("sos_idea"))) record("sos_floor3_coins");
        if(state.rewardFlags.size()>=4 && state.rewardFlags.values().stream().allMatch(Boolean.TRUE::equals)) record("stronghold_of_security");
        for (OneTime reward : KNOWN)
        {
            if (claimed.contains(reward.id)) continue;
            boolean owns = reward.items.stream().anyMatch(name -> state.items.stream().anyMatch(i -> name.equalsIgnoreCase(i.name))
                || state.equipment.stream().anyMatch(i -> name.equalsIgnoreCase(i.name)));
            if (owns) record(reward.id);
        }
    }

    public void refresh(GameState state,List<ResourceItem> bank) {
        refresh(state);
        if(state==null) return;
        for(OneTime reward:KNOWN) if(!done(reward.id) && bank.stream().anyMatch(i->i.quantity>0 && reward.items.contains(i.name))) record(reward.id);
    }

    public synchronized void record(String id)
    {
        if (id == null || !claimed.add(id)) return;
        try { if (ledger != null) FilesStore.write(ledger, claimed.toArray(new String[0])); }
        catch (Exception ex) { DebugLog.event("KNOWLEDGE_SAVE", ex.getClass().getSimpleName()); }
        DebugLog.event("ONE_TIME_DONE", id + " recorded; this reward will not be repeated");
    }

    public boolean done(String id) { return claimed.contains(id); }

    public List<OneTime> doneRewards()
    {
        List<OneTime> result = new ArrayList<>();
        for (OneTime reward : KNOWN) if (done(reward.id)) result.add(reward);
        return result;
    }

    public List<OneTime> available(GameState state)
    {
        refresh(state);
        List<OneTime> result = new ArrayList<>();
        for (OneTime reward : KNOWN) if (!done(reward.id)) result.add(reward);
        return result;
    }

    public List<String> unlocked(GameState state)
    {
        List<String> result = new ArrayList<>();
        if (state == null) return result;
        for (var entry : UNLOCKS.entrySet())
            if ("FINISHED".equals(state.quests.get(entry.getKey()))) result.add(entry.getValue());
        return result;
    }

    public boolean combatReady(GameState state)
    {
        if (state == null) return false;
        for (Method method : Methods.all())
        {
            if (method.kind != Method.Kind.COMBAT) continue;
            if (state.levels.getOrDefault(method.skill, 1) < method.level) continue;
            if (CombatPolicy.needsGear(method) && !ToolPolicy.hasTools(method, state)) continue;
            return true;
        }
        return false;
    }

    public boolean skillingReady(GameState state)
    {
        if (state == null) return false;
        for (Method method : Methods.all())
        {
            if (method.kind != Method.Kind.OBJECT && method.kind != Method.Kind.FISHING
                && method.kind != Method.Kind.PROCESS) continue;
            if (state.levels.getOrDefault(method.skill, 1) < method.level) continue;
            return true;
        }
        return false;
    }

    /** Quests are one-time content; every farmable method may be repeated. */
    public static boolean farmable(Method method)
    {
        return method != null && method.kind != Method.Kind.QUEST;
    }

    public String view() { return view; }

    void updateView(GameState state)
    {
        int questsDone = (int) (state == null ? 0 : state.quests.values().stream().filter("FINISHED"::equals).count());
        List<String> unlocks = unlocked(state);
        view = "One-time: " + doneRewards().size() + "/" + KNOWN.size() + " done"
            + " | quests done " + questsDone
            + " | unlocks " + (unlocks.isEmpty() ? "none yet" : String.join(", ", unlocks))
            + " | combat " + (combatReady(state) ? "ready" : "not ready")
            + " | skilling " + (skillingReady(state) ? "ready" : "not ready");
    }
}
