package com.example.f2paio;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import org.slf4j.LoggerFactory;

public final class DebugLog
{
    private static final ArrayDeque<String> RECENT = new ArrayDeque<>();
    private static ExecutorService writer() { return Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "F2PAIO-debug-writer"); t.setDaemon(true); return t;
    }); }
    private static ExecutorService WRITER = writer();
    private static String last = "";
    private static long lastAt;

    public static synchronized void event(String category, String detail)
    {
        String signature = category + ": " + detail;
        long now = System.currentTimeMillis();
        if (signature.equals(last) && now - lastAt < 10_000) return;
        last = signature; lastAt = now;
        String line = Instant.now() + " " + signature;
        RECENT.addLast(line);
        while (RECENT.size() > 500) RECENT.removeFirst();
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("at", Instant.now().toString()); record.put("category", category);
        record.put("detail", detail); record.put("thread", Thread.currentThread().getName());
        LoggerFactory.getLogger(DebugLog.class).info("F2P AIO {}", signature);
        if (WRITER.isShutdown()) WRITER = writer();
        WRITER.execute(() -> {
            try
            {
                var file = FilesStore.ROOT.resolve("debug-events.jsonl");
                if (Files.exists(file) && Files.size(file) > 5_000_000)
                    Files.move(file, FilesStore.ROOT.resolve("debug-events.previous.jsonl"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                FilesStore.append(file, record);
            }
            catch (IOException ex)
            {
                LoggerFactory.getLogger(DebugLog.class).warn("Debug file write failed: {}", ex.getClass().getSimpleName());
            }
        });
    }

    public static void error(Throwable error)
    {
        StringBuilder trace = new StringBuilder(error.getClass().getName());
        int count = 0;
        for (StackTraceElement frame : error.getStackTrace())
        {
            if (count++ >= 10) break;
            trace.append(" at ").append(frame);
        }
        event("ERROR", trace.toString());
    }

    public static synchronized List<String> recent() { return new ArrayList<>(RECENT); }
    public static synchronized void shutdown() { WRITER.shutdown(); }
    private DebugLog() { }
}
