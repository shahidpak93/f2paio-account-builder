package com.example.f2paio;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.ScriptPauseController;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;

/** Human-like mouse motion engine with overshoot physics, blunder simulation, and off-screen parking. */
public final class HumanInput
{
    private final List<Recorder.Sample> recording;
    private final Random random;

    public HumanInput(List<Recorder.Sample> recording, Random random)
    {
        this.recording = List.copyOf(recording); this.random = random;
    }

    public void hover(Shape shape) throws InterruptedException
    {
        if (shape == null || stopped()) return;
        Rectangle bounds = shape.getBounds();
        Point target = new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
        for (int i = 0; i < 12; i++)
        {
            int x = (int) (bounds.getCenterX() + random.nextGaussian() * bounds.width * 0.15);
            int y = (int) (bounds.getCenterY() + random.nextGaussian() * bounds.height * 0.15);
            if (shape.contains(x, y)) { target = new Point(x, y); break; }
        }
        if (!shape.contains(target)) return;
        moveTo(target);
    }

    public void hoverAdjacent(Shape shape) throws InterruptedException
    {
        if (shape == null || stopped()) return;
        Rectangle bounds = shape.getBounds();
        int offset = (random.nextBoolean() ? 1 : -1) * (18 + random.nextInt(16));
        Point adjacent = new Point((int) bounds.getCenterX() + offset, (int) bounds.getCenterY() + offset);
        moveTo(adjacent);
    }

    public void moveTo(Point target) throws InterruptedException
    {
        if (target == null || stopped()) return;
        Point from = Microbot.getMouse().getMousePosition();
        if (from == null)
        {
            Microbot.getNaturalMouse().moveTo(target.x, target.y);
            return;
        }

        // 15% probability: slight overshoot past target, brief dwell, then micro-correction
        if (random.nextDouble() < 0.15 && Math.hypot(target.x - from.x, target.y - from.y) > 60)
        {
            double dist = Math.hypot(target.x - from.x, target.y - from.y);
            int overX = target.x + (int) Math.round((target.x - from.x) / dist * (4 + random.nextInt(6)));
            int overY = target.y + (int) Math.round((target.y - from.y) / dist * (4 + random.nextInt(6)));
            dispatchSegment(from, new Point(overX, overY));
            Thread.sleep(40 + random.nextInt(70));
            from = Microbot.getMouse().getMousePosition();
            if (from == null || stopped()) return;
        }

        dispatchSegment(from, target);
    }

    public void parkCursorOffscreen() throws InterruptedException
    {
        if (stopped()) return;
        Point from = Microbot.getMouse().getMousePosition();
        if (from == null || from.x < 0) return;
        int canvasHeight = Microbot.getClient().getCanvasHeight();
        Point offscreen = new Point(-20, Math.max(10, random.nextInt(Math.max(20, canvasHeight))));
        moveTo(offscreen);
    }

    public void returnCursorOnscreen() throws InterruptedException
    {
        if (stopped()) return;
        Point from = Microbot.getMouse().getMousePosition();
        if (from == null || from.x >= 0) return;
        int canvasWidth = Microbot.getClient().getCanvasWidth();
        int canvasHeight = Microbot.getClient().getCanvasHeight();
        Point onscreen = new Point(canvasWidth / 4 + random.nextInt(canvasWidth / 2),
            canvasHeight / 4 + random.nextInt(canvasHeight / 2));
        moveTo(onscreen);
    }

    public boolean blunderRoll()
    {
        return random.nextDouble() < 0.035; // 3.5% intentional human blunder
    }

    private void dispatchSegment(Point from, Point target) throws InterruptedException
    {
        List<Recorder.Sample> segment = segment(recording);
        if (segment.size() < 3)
        {
            Microbot.getNaturalMouse().moveTo(target.x, target.y);
            return;
        }
        Recorder.Sample first = segment.get(0), last = segment.get(segment.size() - 1);
        double warp = Math.max(0.75, Math.min(1.35, Distributions.lognormal(random, 1, 0.12)));
        long previous = first.dt;
        for (Recorder.Sample sample : segment)
        {
            if (stopped()) return;
            long delay = Math.max(0, Math.min(100, Math.round((sample.dt - previous) * warp)));
            if (delay > 0) Thread.sleep(delay);
            previous = sample.dt;
            Point point = transform(first, last, sample, from, target);
            Microbot.getMouse().move(point.x, point.y);
        }
    }

    private static boolean stopped() throws InterruptedException
    {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        return InputArbiter.isHuman() || ScriptPauseController.isPaused()
            || Files.exists(FilesStore.ROOT.resolve("STOP"));
    }

    static List<Recorder.Sample> segment(List<Recorder.Sample> samples)
    {
        List<Recorder.Sample> moves = new ArrayList<>();
        for (Recorder.Sample sample : samples)
        {
            if (sample.id == MouseEvent.MOUSE_MOVED || sample.id == MouseEvent.MOUSE_DRAGGED)
            {
                if (!moves.isEmpty() && sample.dt - moves.get(0).dt > 1_500) moves.clear();
                moves.add(sample);
            }
            else if (sample.id == MouseEvent.MOUSE_PRESSED)
            {
                if (moves.size() >= 3 && Math.hypot(sample.x - moves.get(0).x, sample.y - moves.get(0).y) > 30)
                {
                    moves.add(sample); return moves;
                }
                moves.clear();
            }
        }
        return List.of();
    }

    static Point transform(Recorder.Sample first, Recorder.Sample last, Recorder.Sample sample,
        Point from, Point to)
    {
        double dx = last.x - first.x, dy = last.y - first.y;
        double length = dx * dx + dy * dy;
        if (length == 0) return new Point(from);
        double px = sample.x - first.x, py = sample.y - first.y;
        double parallel = (px * dx + py * dy) / length;
        double perpendicular = (py * dx - px * dy) / length;
        double tx = to.x - from.x, ty = to.y - from.y;
        return new Point((int) Math.round(from.x + parallel * tx - perpendicular * ty),
            (int) Math.round(from.y + parallel * ty + perpendicular * tx));
    }
}
