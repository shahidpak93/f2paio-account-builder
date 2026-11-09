package com.example.f2paio;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;

/**
 * Owns the run-orb policy for this plugin.
 *
 * <p>Microbot's inherited {@code Script.run()} loop can turn run on by itself, but this
 * plugin used to force {@code Microbot.enableAutoRunOn = false} and never clicked the orb
 * anywhere else, so every trip was walked. That made travel, quest routes and supply
 * trips roughly twice as slow as a player's.</p>
 *
 * <p>The policy here is deliberately narrow: a long trip with enough energy turns the orb
 * on, short repositioning does not, and the orb is never clicked while a dialogue, bank,
 * shop or GE interface owns input, because that click would steal interface focus and can
 * silently break quest dialogue or offer entry. Once on, run is left on so it resumes as
 * energy regenerates.</p>
 */
public final class RunEnergy
{
    /** Client varp for the run-orb state; Microbot's own helpers read the same id. */
    static final int RUN_VARP = 173;
    /** Raw client energy is 0..10000, i.e. hundredths of a percent. */
    static final int ARM_AT = 2_000;
    /** Repositioning shorter than this does not justify an orb click. */
    static final int MIN_TRIP_TILES = 10;
    /** Minimum spacing between orb clicks so a failed click cannot become a click loop. */
    static final long RETRY_MS = 3_000;

    public enum Decision { ENABLE, HOLD, NONE }

    private static long lastAttemptAt;
    private static long lastPotionAt;
    private static long lastHoldLogAt;
    private static String lastHoldReason = "";

    private RunEnergy() { }

    /**
     * Pure decision shared by the runtime and the offline tests.
     *
     * @param loggedIn      client must be logged in for any click to be meaningful
     * @param interfaceOpen dialogue, bank, shop or GE is open
     * @param runEnabled    orb is already on
     * @param energy        raw client energy, 0..10000
     * @param tiles         estimated walking distance to the destination
     */
    public static Decision decide(boolean loggedIn, boolean interfaceOpen, boolean runEnabled,
        int energy, int tiles)
    {
        if (!loggedIn) return Decision.NONE;
        if (runEnabled) return Decision.NONE;          // already running, or waiting for energy
        if (interfaceOpen) return Decision.HOLD;       // clicking now would steal interface focus
        if (tiles < MIN_TRIP_TILES) return Decision.NONE;
        if (energy < ARM_AT) return Decision.HOLD;     // walk this trip, run a later one
        return Decision.ENABLE;
    }

    /** True while a blocking interface owns input and an orb click must not be sent. */
    public static boolean interfaceOwnsInput()
    {
        try
        {
            return Rs2Dialogue.isInDialogue() || Rs2Bank.isOpen() || Rs2Shop.isOpen()
                || Rs2GrandExchange.isOpen();
        }
        catch (RuntimeException ex)
        {
            return true; // never click when the interface state cannot be read
        }
    }

    /** Called by {@link F2pAioActions#walk} before a route starts. */
    public static void beforeWalk(WorldPoint destination)
    {
        if (destination == null) return;
        long now = System.currentTimeMillis();
        int[] live = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var client = Microbot.getClient();
            if (client.getGameState() != net.runelite.api.GameState.LOGGED_IN) return null;
            var player = client.getLocalPlayer();
            if (player == null || player.getWorldLocation() == null) return null;
            var location = player.getWorldLocation();
            return new int[] { client.getEnergy(), client.getVarpValue(RUN_VARP) == 1 ? 1 : 0,
                location.getX(), location.getY() };
        }).orElse(null);
        if (live == null) return;
        int tiles = Math.max(Math.abs(live[2] - destination.getX()),
            Math.abs(live[3] - destination.getY()));
        if (live[0] < ARM_AT) restoreEnergyPotion();
        manage(live[0], live[1] == 1, tiles, now, "walk");
    }

    /**
     * Opportunistic re-enable while something else moves the player (for example a native
     * quest script during DELEGATE). Cheap enough to run once per engine cycle.
     */
    public static void maintain(GameState state, long now)
    {
        if (state == null || state.runEnabled || !state.moving) return;
        manage(state.runEnergy, false, MIN_TRIP_TILES, now, "moving");
    }

    /** Logs the starting state so the debug log answers "is run even on?". */
    public static void sessionStart()
    {
        lastAttemptAt = 0;
        int[] live = Microbot.getClientThread().runOnClientThreadOptional(() -> new int[] {
            Microbot.getClient().getEnergy(),
            Microbot.getClient().getVarpValue(RUN_VARP) == 1 ? 1 : 0 }).orElse(null);
        if (live == null) return;
        DebugLog.event("RUN_STATE", "energy=" + percent(live[0]) + "% enabled=" + (live[1] == 1)
            + "; policy: enable on trips>=" + MIN_TRIP_TILES + " tiles above " + percent(ARM_AT) + "%");
    }

    public static void reset() { lastAttemptAt = 0; }

    /** Short run status for the panel/travel status line. */
    public static String summary(GameState state)
    {
        if (state == null || state.runEnergy < 0) return "";
        return " (run " + state.runPercent() + "% " + (state.runEnabled ? "on" : "off") + ")";
    }

    static int percent(int raw) { return Math.max(0, Math.min(100, raw / 100)); }

    private static void manage(int energy, boolean enabled, int tiles, long now, String trigger)
    {
        boolean interfaceOpen = interfaceOwnsInput();
        Decision decision = decide(true, interfaceOpen, enabled, energy, tiles);
        switch (decision)
        {
            case ENABLE:
                if (now - lastAttemptAt < RETRY_MS) return;
                lastAttemptAt = now;
                boolean observed = Rs2Player.toggleRunEnergy(true);
                DebugLog.event("RUN_ENABLE", "energy=" + percent(energy) + "% tiles=" + tiles
                    + " trigger=" + trigger + " observed=" + observed);
                break;
            case HOLD:
                String reason = interfaceOpen ? "blocking interface owns input" : "energy below arm threshold";
                if (!reason.equals(lastHoldReason) || now - lastHoldLogAt >= 20_000)
                {
                    lastHoldReason = reason; lastHoldLogAt = now;
                    DebugLog.event("RUN_HOLD", "energy=" + percent(energy) + "% tiles=" + tiles
                        + " reason=" + reason);
                }
                break;
            default:
                break;
        }
    }

    /** Use a carried usable energy potion without interrupting another interface. */
    private static void restoreEnergyPotion()
    {
        long now=System.currentTimeMillis();
        if(now-lastPotionAt<3_000 || interfaceOwnsInput()) return;
        var potion = Rs2Inventory.get(i -> !i.isNoted() && i.getName() != null && i.getName().startsWith("Energy potion"));
        if (potion == null) return;
        lastPotionAt=now;
        DebugLog.event("TRAVEL_POTION", "Drink carried energy potion; submitted="
            + Rs2Inventory.interact(potion.getId(), "Drink"));
    }
}
