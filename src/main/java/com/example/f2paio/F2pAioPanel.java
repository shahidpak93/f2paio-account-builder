package com.example.f2paio;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public final class F2pAioPanel extends JFrame
{
    private final F2pAioScript script;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "F2PAIO-controls"); t.setDaemon(true); return t;
    });
    private F2pAioConfig config = new F2pAioConfig();
    private final JLabel status = new JLabel("Ready - press Start auto");
    private final JLabel learning = new JLabel("Learning starts with your account snapshot");
    private final JLabel totals = new JLabel("XP: 0   Levels gained: 0   Quests completed: 0");
    private final JLabel money = new JLabel("Coins carried: waiting for snapshot");
    private final JLabel position = new JLabel("Position: waiting for snapshot");
    private final JLabel notice = new JLabel("Account builder: quests, levels and unlocks. Funding only when required.");
    private final JButton start = new JButton("Start auto");
    private final DefaultTableModel skills = table("Skill", "Level", "XP gained");
    private final DefaultTableModel quests = table("Quest", "Current state");
    private final DefaultTableModel inventory = table("Location", "Item", "Quantity");
    private final JTextArea plan = new JTextArea();
    private final JTextArea events = new JTextArea();
    private final JComboBox<String> backend = new JComboBox<>(new String[]{"OLLAMA", "LM_STUDIO", "GEMINI", "OPENAI_COMPATIBLE"});
    private final JTextField endpoint = new JTextField();
    private final JTextField model = new JTextField();
    private final JPasswordField key = new JPasswordField();
    private final JCheckBox llm = new JCheckBox("Use local agent team");
    private final JCheckBox remember = new JCheckBox("Remember key with Windows DPAPI");
    // GE Flipping controls
    private final JCheckBox enableFlip = new JCheckBox("Use installed GE Flipper with active Copilot");
    private final JTextField flipMinBudget = new JTextField("10000");
    private final JTextField flipMinCoinsAfter = new JTextField("5000");
    private final Timer timer;
    private volatile boolean closed;

    public F2pAioPanel(F2pAioScript script)
    {
        this.script = script;
        setTitle("F2P AIO " + Tuning.VERSION + " - Auto dashboard");
        setDefaultCloseOperation(HIDE_ON_CLOSE); setSize(850, 720); setLocationRelativeTo(null);
        JPanel header = new JPanel(new GridLayout(0, 1, 0, 6));
        header.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        header.add(status); header.add(totals); header.add(money); header.add(position); header.add(learning);
        add(header, BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Skills", new JScrollPane(new JTable(skills)));
        tabs.addTab("Quests", new JScrollPane(new JTable(quests)));
        tabs.addTab("Inventory / bank", new JScrollPane(new JTable(inventory)));
        events.setEditable(false); events.setLineWrap(true); events.setWrapStyleWord(true);
        tabs.addTab("Activity / debug", new JScrollPane(events));
        plan.setEditable(false); plan.setLineWrap(true); plan.setWrapStyleWord(true);
        tabs.addTab("Plan / learning",new JScrollPane(plan));
        JPanel ai = new JPanel(new GridLayout(0, 2, 8, 10));
        ai.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        ai.add(new JLabel("Optional planner")); ai.add(llm);
        ai.add(new JLabel("Backend")); ai.add(backend);
        ai.add(new JLabel("Endpoint")); ai.add(endpoint);
        ai.add(new JLabel("Model")); ai.add(model);
        ai.add(new JLabel("API key")); ai.add(key);
        ai.add(new JLabel("Storage")); ai.add(remember);
        JButton save = new JButton("Save AI settings"); save.addActionListener(e -> save(false));
        ai.add(save); ai.add(new JLabel("Changes apply on next Start"));
        JPanel aiTop = new JPanel(new BorderLayout()); aiTop.add(ai, BorderLayout.NORTH);
        tabs.addTab("AI settings", aiTop);
        // GE Flipping tab
        JPanel flip = new JPanel(new GridLayout(0, 2, 8, 10));
        flip.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        flip.add(new JLabel("GE Flipping")); flip.add(enableFlip);
        flip.add(new JLabel("Min coins to start flip")); flip.add(flipMinBudget);
        flip.add(new JLabel("Request Finish below free GP")); flip.add(flipMinCoinsAfter);
        flip.add(new JLabel("How it works")); flip.add(new JLabel("<html>Delegates to your installed GE Flipper and active Copilot; keeps its existing settings and finishes owned offers before resuming</html>"));
        JPanel flipTop = new JPanel(new BorderLayout()); flipTop.add(flip, BorderLayout.NORTH);
        tabs.addTab("GE Flipping", flipTop);
        add(tabs, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout()); JPanel buttons = new JPanel(new FlowLayout());
        JButton stop = new JButton("Stop"); JButton export = new JButton("Export debug report");
        JButton skip = new JButton("Skip task"); JButton resume = new JButton("Resume scripts");
        JButton chat = new JButton("AI chat");
        skip.addActionListener(e -> worker.execute(script::skip));
        resume.addActionListener(e -> worker.execute(script::resume));
        chat.addActionListener(e -> new AiChatWindow(SwingUtilities.getWindowAncestor(this), script).setVisible(true));
        start.addActionListener(e -> save(true)); stop.addActionListener(e -> worker.execute(script::shutdown));
        export.addActionListener(e -> export());
        buttons.add(start); buttons.add(skip); buttons.add(resume); buttons.add(chat); buttons.add(stop); buttons.add(export);
        bottom.add(buttons, BorderLayout.NORTH);
        bottom.add(notice, BorderLayout.SOUTH); add(bottom, BorderLayout.SOUTH);
        backend.addActionListener(e -> defaults()); apply(config);
        start.setEnabled(false); worker.execute(this::load);
        timer = new Timer(700, e -> refresh()); timer.start();
    }

    private static DefaultTableModel table(String... columns)
    {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    private void refresh()
    {
        learning.setText(script.learning());
        String currentPlan=script.planView(); if(!currentPlan.equals(plan.getText())) plan.setText(currentPlan);
        status.setText((script.active() ? "AUTO: " : "STOPPED: ") + script.status());
        ProgressTracker.Snapshot p = script.progress();
        totals.setText("XP gained: " + p.xpGained + "   Levels gained: " + p.levelUps
            + "   Quests completed this session: " + p.newQuests + "   Total completed: " + p.completedQuests);
        money.setText("Coins carried: " + p.coins + "   Change: " + (p.coinChange >= 0 ? "+" : "") + p.coinChange
            + "   (coin balance change, not profit)");
        position.setText("Position: " + p.position + "   Last observation: "
            + (p.observedAt == 0 ? "waiting" : Math.max(0, (System.currentTimeMillis() - p.observedAt) / 1000) + "s ago"));
        skills.setRowCount(0); p.levels.forEach((name, value) -> skills.addRow(new Object[]{name, value, p.xpBySkill.get(name)}));
        quests.setRowCount(0); p.quests.forEach((name, value) -> quests.addRow(new Object[]{name, value}));
        inventory.setRowCount(0);
        p.items.forEach(item -> inventory.addRow(new Object[]{"Inventory", item.name, item.quantity}));
        p.equipment.forEach(item -> inventory.addRow(new Object[]{"Equipment", item.name, item.quantity}));
        script.bankItems().forEach(item -> inventory.addRow(new Object[]{"Bank (last observed)", item.name, item.quantity}));
        String text = String.join("\n", DebugLog.recent());
        if (!text.equals(events.getText())) { events.setText(text); events.setCaretPosition(events.getDocument().getLength()); }
    }

    private void defaults()
    {
        switch ((String) backend.getSelectedItem())
        {
            case "OLLAMA": endpoint.setText("http://localhost:11434/api/generate"); model.setText("qwen2.5:7b"); break;
            case "LM_STUDIO": endpoint.setText("http://localhost:1234/v1/chat/completions"); model.setText("local-model"); break;
            case "GEMINI": endpoint.setText("https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"); model.setText("gemini-2.5-flash"); break;
            default: endpoint.setText("https://api.example.com/v1/chat/completions"); model.setText("your-model");
        }
    }

    private void apply(F2pAioConfig c)
    {
        config = c; config.autonomous = true; config.taskMode = "AIO";
        llm.setSelected(c.llmEnabled); backend.setSelectedItem(c.llmBackend);
        endpoint.setText(c.llmEndpoint); model.setText(c.llmModel);
        enableFlip.setSelected(c.enableFlipping);
        flipMinBudget.setText(String.valueOf(c.flipMinBudget));
        flipMinCoinsAfter.setText(String.valueOf(c.flipMinCoinsAfter));
    }

    private void load()
    {
        try
        {
            var path = FilesStore.ROOT.resolve("panel.json");
            F2pAioConfig c = Files.exists(path) ? FilesStore.read(path, F2pAioConfig.class) : new F2pAioConfig();
            c.autonomous = true; c.taskMode = "AIO"; c.validate(); String secret = SecretStore.load();
            SwingUtilities.invokeLater(() -> { apply(c); key.setText(secret); remember.setSelected(!secret.isEmpty()); });
        }
        catch (Exception ex) { DebugLog.error(ex); message("Could not load settings: " + ex.getClass().getSimpleName()); }
        finally { SwingUtilities.invokeLater(() -> start.setEnabled(true)); }
    }

    private void save(boolean run)
    {
        F2pAioConfig c = config.copy(); c.autonomous = true; c.taskMode = "AIO";
        c.llmEnabled = llm.isSelected(); c.llmBackend = (String) backend.getSelectedItem();
        c.llmEndpoint = endpoint.getText().trim(); c.llmModel = model.getText().trim();
        c.enableFlipping = enableFlip.isSelected();
        try { c.flipMinBudget = Math.max(1_000, Integer.parseInt(flipMinBudget.getText().trim())); } catch (NumberFormatException ignored) { }
        try { c.flipMinCoinsAfter = Math.max(100, Integer.parseInt(flipMinCoinsAfter.getText().trim())); } catch (NumberFormatException ignored) { }
        try { c.validate(); } catch (IllegalArgumentException ex) { message(ex.getMessage()); return; }
        char[] chars = key.getPassword(); String secret = new String(chars); Arrays.fill(chars, '\0');
        boolean persist = remember.isSelected(); start.setEnabled(false);
        worker.execute(() -> {
            try
            {
                FilesStore.write(FilesStore.ROOT.resolve("panel.json"), c);
                if (persist && !secret.isEmpty()) SecretStore.save(secret); else SecretStore.forget();
                if (!closed && run)
                {
                    boolean started = script.run(c, secret);
                    if (closed) script.shutdown();
                    message(started ? "Autonomous session started" : script.status());
                }
                else message("AI settings saved");
            }
            catch (Exception ex)
            {
                DebugLog.error(ex);
                message(ex instanceof IllegalArgumentException ? ex.getMessage() : "Failed: " + ex.getClass().getSimpleName());
            }
            finally { SwingUtilities.invokeLater(() -> start.setEnabled(true)); }
        });
    }

    private void export()
    {
        worker.execute(() -> {
            try
            {
                var file = FilesStore.ROOT.resolve("debug-report.json");
                var report = new LinkedHashMap<String, Object>();
                report.put("version", Tuning.VERSION); report.put("status", script.status());
                report.put("learning",script.learning()); report.put("plan",script.planView()); report.put("progress", script.progress()); report.put("events", DebugLog.recent());
                FilesStore.write(file, report); message("Saved " + file);
            }
            catch (Exception ex) { DebugLog.error(ex); message("Debug export failed"); }
        });
    }

    private void message(String value) { SwingUtilities.invokeLater(() -> notice.setText(value)); }
    public void close() { closed = true; timer.stop(); worker.shutdownNow(); dispose(); }
}
