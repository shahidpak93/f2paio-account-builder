package com.example.f2paio;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class PanelPreview
{
    public static void main(String[] args) throws Exception
    {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeLater(() -> {
            F2pAioScript script = new F2pAioScript();
            F2pAioPanel panel = new F2pAioPanel(script);
            panel.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosing(java.awt.event.WindowEvent event)
                {
                    panel.close(); script.disposeForUnload(); System.exit(0);
                }
            });
            panel.setVisible(true);
        });
    }
}
