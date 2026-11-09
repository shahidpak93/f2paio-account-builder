package com.example.f2paio;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

public final class ProductionActions
{
    public boolean continuePrompt()
    {
        if (!Rs2Widget.isWidgetVisible(270, 0)) return false;
        Rs2Keyboard.keyPress(java.awt.event.KeyEvent.VK_SPACE);
        DebugLog.event("PRODUCTION_PROMPT", "Confirmed visible native production interface");
        return true;
    }

    public boolean continuePrompt(Method method) {
        F2pProduction.Spec spec=F2pProduction.get(method.id);
        if (method.id.startsWith("smelt_") || (spec!=null && "leather".equals(spec.operation)))
            return Rs2Widget.isWidgetVisible(270,0) && Rs2Widget.handleProcessingInterface(method.products.get(0));
        if (method.id.startsWith("forge_"))
            return Rs2Widget.isWidgetVisible(312,0)
                && Rs2Widget.clickWidget("Dagger",java.util.Optional.of(312),0,false);
        if (spec!=null && spec.altar()) return false;
        return continuePrompt();
    }

    public boolean leaveAltar() {
        var portal=target("Portal");
        return portal!=null && portal.click("Exit");
    }

    public boolean enterAltar(F2pProduction.Spec spec,GameState state) {
        var ruins=target("Mysterious ruins");
        if (ruins==null) return false;
        if (spec.equipped(state)) return ruins.click("Enter");
        return Rs2Inventory.useUnNotedItemOnObject(spec.accessName,ruins);
    }

    private net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel target(String name) {
        return Microbot.getRs2TileObjectCache().query().where(o -> name.equalsIgnoreCase(o.getName()))
            .within(12).nearestOnClientThread();
    }

    public boolean step(Method method,GameState state)
    {
        if (continuePrompt(method)) return true;
        F2pProduction.Spec spec=F2pProduction.get(method.id);
        if (spec!=null && spec.altar()) {
            if (spec.inside(state)) {
                var altar = target("Altar");
                return altar!=null && altar.click("Craft-rune");
            }
            return false;
        }
        if (spec!=null && "leather".equals(spec.operation)) {
            if (Rs2Widget.isWidgetVisible(270,0)) return false;
            return Rs2Inventory.combine(1733,1741);
        }
        if (method.id.startsWith("smelt_")) {
            if (Rs2Widget.isWidgetVisible(270,0)) return false;
            var furnace = target("Furnace");
            return furnace!=null && furnace.click("Smelt");
        }
        if (method.id.startsWith("forge_")) {
            if (Rs2Widget.isWidgetVisible(312,0)) return false;
            var anvil = target("Anvil");
            return anvil!=null && Rs2Inventory.useUnNotedItemOnObject(ProductionRecipes.inputs(method.id).get(1).name,anvil);
        }
        var inputs = ProductionRecipes.inputs(method.id);
        if(method.id.startsWith("pray_")) return Rs2Inventory.interact(inputs.get(0).id,"Bury");
        if(method.id.startsWith("burn_")) return Rs2Inventory.combine(inputs.get(0).id,inputs.get(1).id);
        if (method.id.startsWith("tan_"))
        {
            int widget = method.id.equals("tan_soft") ? Tuning.TAN_SOFT_ALL : Tuning.TAN_HARD_ALL;
            if (Rs2Widget.isWidgetVisible(widget)) return Rs2Widget.clickWidget(widget);
            var ellis = Microbot.getRs2NpcCache().query().withName("Ellis").nearestOnClientThread();
            return ellis != null && ellis.click("Trade");
        }
        if (method.id.startsWith("cook_"))
        {
            var range = Microbot.getRs2TileObjectCache().query().where(o -> o.getName() != null
                && o.getName().equalsIgnoreCase("Range")).within(12).nearestOnClientThread();
            return range != null && Rs2Inventory.useUnNotedItemOnObject(inputs.get(0).name, range);
        }
        return inputs.size() == 2 && Rs2Inventory.combine(inputs.get(0).id, inputs.get(1).id);
    }
}
