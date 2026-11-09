package com.example.f2paio;

import java.awt.AWTEvent;
import java.awt.Canvas;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class Recorder implements AutoCloseable
{
    public static final class Sample
    {
        public final int id;
        public final int x;
        public final int y;
        public final long dt;
        public Sample(int id, int x, int y, long dt)
        {
            this.id = id; this.x = x; this.y = y; this.dt = dt;
        }
    }

    private final List<Sample> samples = new ArrayList<>();
    private AWTEventListener listener;
    private long started;

    public synchronized void start(Canvas canvas)
    {
        close(); samples.clear(); started = System.nanoTime();
        listener = event -> {
            if (!(event instanceof MouseEvent) || event.getSource() != canvas) return;
            MouseEvent mouse = (MouseEvent) event;
            synchronized (this)
            {
                if (samples.size() < Tuning.RECORDER_CAPACITY)
                    samples.add(new Sample(mouse.getID(), mouse.getX(), mouse.getY(),
                        (System.nanoTime() - started) / 1_000_000));
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(listener,
            AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    public synchronized List<Sample> stop()
    {
        close(); return List.copyOf(samples);
    }

    public static Path save(List<Sample> samples) throws IOException
    {
        Path directory = FilesStore.ROOT.resolve("motion"); Files.createDirectories(directory);
        Path file = directory.resolve(Instant.now().toString().replace(':', '-') + ".csv");
        StringBuilder csv = new StringBuilder("id,x,y,dt\n");
        for (Sample sample : samples)
            csv.append(sample.id).append(',').append(sample.x).append(',').append(sample.y)
                .append(',').append(sample.dt).append('\n');
        Files.writeString(file, csv.toString());
        return file;
    }

    public static List<Sample> latest() throws IOException
    {
        Path directory = FilesStore.ROOT.resolve("motion");
        if (!Files.isDirectory(directory)) return List.of();
        Path newest;
        try (var stream = Files.list(directory))
        {
            newest = stream.filter(p -> p.toString().endsWith(".csv"))
                .max(java.util.Comparator.comparing(p -> p.getFileName().toString())).orElse(null);
        }
        if (newest == null || Files.size(newest) > 100_000) return List.of();
        List<Sample> result = new ArrayList<>();
        for (String line : Files.readAllLines(newest))
        {
            if (line.equals("id,x,y,dt") || line.isBlank()) continue;
            String[] cells = line.split(",");
            if (cells.length != 4 || result.size() >= Tuning.RECORDER_CAPACITY) break;
            try
            {
                Sample sample = new Sample(Integer.parseInt(cells[0]), Integer.parseInt(cells[1]),
                    Integer.parseInt(cells[2]), Long.parseLong(cells[3]));
                if (sample.dt < 0 || (!result.isEmpty() && sample.dt < result.get(result.size() - 1).dt))
                    throw new IOException("Motion timestamps out of order");
                result.add(sample);
            }
            catch (NumberFormatException ex) { throw new IOException("Invalid motion CSV", ex); }
        }
        return result;
    }

    @Override public synchronized void close()
    {
        if (listener != null)
        {
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener); listener = null;
        }
    }
}
