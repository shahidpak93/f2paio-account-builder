package com.example.f2paio;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@PluginDescriptor(name = "F2P AIO", configName = "f2paio", description = "Custom activity scheduler with local and cloud planning",
    tags = {"f2p", "aio", "skilling"}, version = Tuning.VERSION, minClientVersion = "2.6.54",
    enabledByDefault = false, isExternal = true)
public final class F2pAioPlugin extends Plugin
{
    @Inject private F2pAioScript script;
    @Inject private ClientToolbar toolbar;
    private F2pAioPanel panel;
    private NavigationButton button;
    private volatile boolean enabled;

    @net.runelite.client.eventbus.Subscribe
    public void onChatMessage(net.runelite.api.events.ChatMessage event)
    {
        if (event.getType() == net.runelite.api.ChatMessageType.GAMEMESSAGE
            || event.getType() == net.runelite.api.ChatMessageType.SPAM)
            script.gameMessage(net.runelite.client.util.Text.removeTags(event.getMessage()));
    }

    @Override protected void startUp()
    {
        enabled = true;
        SwingUtilities.invokeLater(() -> {
            if (!enabled) return;
            if (panel == null) panel = new F2pAioPanel(script);
            if (button == null)
            {
                BufferedImage icon = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = icon.createGraphics();
                graphics.setColor(new Color(65, 164, 124)); graphics.fillRoundRect(1, 1, 22, 22, 6, 6);
                graphics.setColor(Color.WHITE); graphics.drawString("A", 8, 17); graphics.dispose();
                button = NavigationButton.builder().tooltip("F2P AIO control panel").icon(icon)
                    .tab(false).priority(8).onClick(() -> { panel.setVisible(true); panel.toFront(); }).build();
                toolbar.addNavigation(button);
            }
            panel.setVisible(true);
        });
    }

    @Override protected void shutDown()
    {
        enabled = false;
        script.shutdown();
        DebugLog.shutdown();
        SwingUtilities.invokeLater(() -> {
            if (button != null) { toolbar.removeNavigation(button); button = null; }
            if (panel != null) { panel.close(); panel = null; }
        });
    }
}
