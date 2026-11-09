package com.example.f2paio;

/** Offline checks for the AI action whitelist. */
public final class AiBridgeTests
{
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }

    static void run()
    {
        check(AiBridge.allowed("RESUME"), "Resume is a whitelisted action");
        check(AiBridge.allowed("bank_leftovers"), "Action matching is case-insensitive");
        check(AiBridge.allowed("NONE"), "NONE is valid");
        check(!AiBridge.allowed("EXECUTE_SHELL"), "Arbitrary actions are rejected");
        check(!AiBridge.allowed("RUN_SCRIPT"), "Script execution is not a chat action");
        check(!AiBridge.allowed(null), "Null is rejected");
        check(AiBridge.SAFE_ACTIONS.size() == 7, "Whitelist stays small and bounded");
        System.out.println("PASS " + checks + " AI action-whitelist cases");
    }
}
