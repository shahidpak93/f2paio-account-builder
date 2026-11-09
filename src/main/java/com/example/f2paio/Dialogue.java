package com.example.f2paio;

import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;

public final class Dialogue
{
    public boolean step(Watchdog watchdog, String... options)
    {
        if (Rs2Dialogue.hasSelectAnOption())
        {
            watchdog.dialogue("options:" + Rs2Dialogue.getQuestion());
            if (options.length > 0) return Rs2Dialogue.clickOption(options);
            return false;
        }
        if (Rs2Dialogue.hasContinue())
        {
            watchdog.dialogue(Rs2Dialogue.getDialogueText());
            Rs2Dialogue.clickContinue();
            return true;
        }
        return false;
    }
}
