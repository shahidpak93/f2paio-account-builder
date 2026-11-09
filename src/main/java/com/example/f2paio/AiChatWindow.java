package com.example.f2paio;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.util.concurrent.CompletableFuture;
import javax.swing.*;

/** Chat with the local agent team about the live session; replies are diagnostic and bounded. */
public final class AiChatWindow extends JDialog
{
    private final F2pAioScript script;
    private String lastUserMessage="";
    private final JTextArea transcript = new JTextArea();
    private final JTextField input = new JTextField();
    private final JLabel status = new JLabel("Ask about an error, a decision, or a goal.");
    private final JButton send = new JButton("Send");
    private final JButton repair = new JButton("Auto-fix with Hermes");
    private final JButton jobs = new JButton("Repair jobs");
    private final JButton apply = new JButton("Apply suggested action");

    public AiChatWindow(Window owner, F2pAioScript script)
    {
        super(owner, "F2P AIO - AI chat", ModalityType.MODELESS);
        this.script = script;
        setSize(720, 480); setLocationRelativeTo(owner);
        transcript.setEditable(false); transcript.setLineWrap(true); transcript.setWrapStyleWord(true);
        add(new JScrollPane(transcript), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(6, 6));
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        bottom.add(input, BorderLayout.CENTER);
        JPanel buttons = new JPanel();
        buttons.add(send); buttons.add(repair); buttons.add(jobs); buttons.add(apply);
        bottom.add(buttons, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
        add(status, BorderLayout.NORTH);
        apply.setEnabled(false);
        send.addActionListener(e -> ask());
        repair.addActionListener(e -> {String issue=input.getText().trim();input.setText("/repair "+(issue.isEmpty()?lastUserMessage:issue));ask();});
        jobs.addActionListener(e -> CompletableFuture.supplyAsync(AiBridge::jobs).whenComplete((value,error)->
            SwingUtilities.invokeLater(()-> {
                if(value==null || !value.has("jobs")) {transcript.append("Developer jobs unavailable\n");return;}
                for(var row:value.getAsJsonArray("jobs")) {
                    var job=row.getAsJsonObject();transcript.append("Job "+job.get("id").getAsString()+": "+job.get("status").getAsString()+"\n");
                    if(job.has("summary")) transcript.append(job.get("summary").getAsString()+"\n");
                    if(job.has("error")) transcript.append("Error: "+job.get("error").getAsString()+"\n");
                }
            })));

        input.addActionListener(e -> ask());
        apply.addActionListener(e ->
        {
            String action = script.lastSuggestedAction();
            if (AiBridge.allowed(action) && !"NONE".equals(action))
            {
                script.queueSafeAction(action);
                transcript.append("\n[applied " + action + " on next cycle]\n");
                apply.setEnabled(false);
            }
        });
        input.setPreferredSize(new Dimension(400, 28));
    }

    private void ask()
    {
        String text = input.getText() == null ? "" : input.getText().trim();
        if (text.isEmpty()) return;
        if(!text.startsWith("/repair")) lastUserMessage=text;
        input.setText("");
        transcript.append("You: " + text + "\n");
        send.setEnabled(false); status.setText("Agent team is reading the live state, logs and account knowledge...");
        final String context = script.aiContext();
        CompletableFuture.supplyAsync(() -> AiBridge.chat(text, context)).whenComplete((reply, error) ->
            SwingUtilities.invokeLater(() ->
            {
                send.setEnabled(true);
                if (error != null || reply == null) { transcript.append("AI: no reply (" + (error == null ? "unknown" : error.getClass().getSimpleName()) + ")\n\n"); return; }
                transcript.append("AI: " + reply.reply + "\n");
                transcript.append("[" + reply.action + "] " + reply.reason + "  model=" + reply.model + "\n\n");
                status.setText(reply.error == null ? "AI replied. Auto-fix submits an autonomous Hermes developer job." : "Fallback reply: " + reply.error);
                script.setLastSuggestedAction(reply.action);
                apply.setEnabled(AiBridge.allowed(reply.action) && !"NONE".equals(reply.action));
                transcript.setCaretPosition(transcript.getDocument().getLength());
            }));
    }
}
