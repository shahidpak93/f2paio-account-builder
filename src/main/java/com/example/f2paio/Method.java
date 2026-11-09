package com.example.f2paio;

import java.util.List;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

public final class Method
{
    public enum Kind { OBJECT, FISHING, COMBAT, QUEST, GROUND, PROCESS }
    public final String id;
    public final String label;
    public final Kind kind;
    public final Skill skill;
    public final int level;
    public final String[] names;
    public final String action;
    public final WorldPoint location;
    public final List<String> products;
    public final List<String> tools;

    public Method(String id, String label, Kind kind, Skill skill, int level, String[] names,
        String action, WorldPoint location, List<String> products, List<String> tools)
    {
        this.id = id;
        this.label = label;
        this.kind = kind;
        this.skill = skill;
        this.level = level;
        this.names = names.clone();
        this.action = action;
        this.location = location;
        this.products = List.copyOf(products);
        this.tools = List.copyOf(tools);
    }

    @Override public String toString() { return label; }
}
