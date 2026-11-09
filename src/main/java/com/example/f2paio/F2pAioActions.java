package com.example.f2paio;

import java.util.Arrays;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.api.coords.WorldPoint;

public final class F2pAioActions
{
    private final HumanInput input;
    public F2pAioActions(HumanInput input) { this.input = input; }

    public boolean object(Method method) throws InterruptedException
    {
        GameState live = GameState.capture();
        if (live == null || !ToolPolicy.hasTools(method, live))
        { DebugLog.event("PRECONDITION_FAIL", method.id + " missing usable tool; no click sent"); return false; }
        var object = Microbot.getRs2TileObjectCache().query()
            .where(o -> Arrays.stream(method.names).anyMatch(n -> n.equalsIgnoreCase(o.getName()))
                && o.hasAction(method.action))
            .within(Tuning.INTERACTION_RANGE).nearestOnClientThread();
        if (object == null) { DebugLog.event("NO_TARGET", "No loaded object for " + method.id); return false; }
        var polygon = Microbot.getClientThread().runOnClientThreadOptional(object::getCanvasTilePoly).orElse(null);
        if (input.blunderRoll() && polygon != null)
        {
            input.hoverAdjacent(polygon);
            Thread.sleep(420);
        }
        input.hover(polygon);
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        if (java.nio.file.Files.exists(FilesStore.ROOT.resolve("STOP"))) return false;
        boolean result = object.click(method.action);
        DebugLog.event("ACTION_SUBMITTED", method.id + " object " + method.action + " submitted=" + result);
        return result;
    }

    public boolean npc(Method method) throws InterruptedException
    {
        var npc = Microbot.getRs2NpcCache().query()
            .where(n -> Arrays.stream(method.names).anyMatch(name -> name.equalsIgnoreCase(n.getName()))
                && !n.isDead() && (method.kind != Method.Kind.COMBAT || !n.isInteracting()))
            .within(Tuning.INTERACTION_RANGE).nearestOnClientThread();
        if (npc == null) return false;
        var hull = Microbot.getClientThread().runOnClientThreadOptional(npc::getConvexHull).orElse(null);
        if (input.blunderRoll() && hull != null)
        {
            input.hoverAdjacent(hull);
            Thread.sleep(420);
        }
        input.hover(hull);
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        if (java.nio.file.Files.exists(FilesStore.ROOT.resolve("STOP"))) return false;
        boolean result = npc.click(method.action);
        DebugLog.event("ACTION_SUBMITTED", method.id + " NPC " + method.action + " submitted=" + result);
        return result;
    }

    public void walk(WorldPoint point) { walk(point,Tuning.TRAVEL_RADIUS); }
    public void walk(WorldPoint point,int radius)
    {
        RunEnergy.beforeWalk(point);
        DebugLog.event("WALK_START", "target=" + point + " full transport-capable walker");
        var result = Rs2Walker.walkWithState(point, radius);
        DebugLog.event("WALK_RESULT", "target=" + point + " result=" + result);
    }

    public boolean ground(Method method) throws InterruptedException
    {
        var item = GroundLoot.nearest(method.products, Tuning.INTERACTION_RANGE, method.id);
        if (item == null) return false;
        var local = item.getLocalLocation();
        if (local != null)
        {
            var poly = net.runelite.client.plugins.microbot.util.camera.Rs2Camera.getCanvasTilePoly(local).orElse(null);
            if (poly != null) input.hover(poly);
        }
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        if (java.nio.file.Files.exists(FilesStore.ROOT.resolve("STOP"))) return false;
        GroundLoot.submitted(method.id,item);
        boolean submitted = item.click("Take");
        DebugLog.event("ACTION_SUBMITTED", "Take " + item.getName() + "; submitted=" + submitted);
        return submitted;
    }

    public boolean localTarget(Method method)
    {
        if (method.kind == Method.Kind.GROUND)
            return GroundLoot.nearest(method.products,Tuning.INTERACTION_RANGE,method.id) != null;
        if (method.kind == Method.Kind.FISHING || method.kind == Method.Kind.COMBAT)
            return Microbot.getRs2NpcCache().query().where(n -> Arrays.stream(method.names)
                .anyMatch(name -> name.equalsIgnoreCase(n.getName())) && !n.isDead()
                && (method.kind != Method.Kind.COMBAT || !n.isInteracting()))
                .within(Tuning.INTERACTION_RANGE).nearestOnClientThread() != null;
        if (method.kind != Method.Kind.OBJECT) return false;
        return Microbot.getRs2TileObjectCache().query()
            .where(o -> Arrays.stream(method.names).anyMatch(n -> n.equalsIgnoreCase(o.getName()))
                && o.hasAction(method.action)).within(Tuning.INTERACTION_RANGE).nearestOnClientThread() != null;
    }

    public boolean hasTools(Method method)
    {
        GameState state = GameState.capture();
        return state != null && ToolPolicy.hasTools(method, state);
    }
}
