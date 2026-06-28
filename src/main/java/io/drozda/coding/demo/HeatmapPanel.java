package io.drozda.coding.demo;

import javax.swing.*;
import java.awt.*;
import java.util.Random;

public class HeatmapPanel extends JPanel {

    private static final int PRICE_LEVELS = 200;
    private static final int TIME_BUCKETS = 800;
    private static final int TIMER_MS = 100;
    private static final int RANDOM_UPDATES_PER_TICK = 100;

    private final int[][] heatmap = new int[PRICE_LEVELS][TIME_BUCKETS];
    private final Random random = new Random();
    private final InfoFrame infoFrame;

    private long fpsCounterStart = System.nanoTime();
    private long fpsFrameCount = 0;
    private double fps = 0.0;
    private long tickCount = 0;

    public HeatmapPanel(InfoFrame infoFrame) {
        this.infoFrame = infoFrame;

        Timer timer = new Timer(TIMER_MS, e -> {
            Profiler.measure(Profiler.EventType.GENERATE_DATA, this::generateFakeData);
            updateDebugInfo();
            repaint();
        });
        timer.start();
    }

    private void generateFakeData() {
        // shift time left
        for (int y = 0; y < PRICE_LEVELS; y++) {
            System.arraycopy(heatmap[y], 1, heatmap[y], 0, TIME_BUCKETS - 1);
            heatmap[y][TIME_BUCKETS - 1] = 0;
        }

        int middle = PRICE_LEVELS / 2;

        for (int i = 0; i < RANDOM_UPDATES_PER_TICK; i++) {
            int price = middle + random.nextInt(80) - 40;

            if (price >= 0 && price < PRICE_LEVELS) {
                heatmap[price][TIME_BUCKETS - 1] += random.nextInt(100);
            }
        }

        tickCount++;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Profiler.measure(Profiler.EventType.PAINT, () -> draw(g));
    }

    private void draw(Graphics g) {
        super.paintComponent(g);

        fpsFrameCount++;
        long now = System.nanoTime();

        if (now - fpsCounterStart >= 1_000_000_000L) {
            fps = fpsFrameCount;
            fpsFrameCount = 0;
            fpsCounterStart = now;
        }

        int w = getWidth();
        int h = getHeight();

        double cellW = w / (double) TIME_BUCKETS;
        double cellH = h / (double) PRICE_LEVELS;

        for (int y = 0; y < PRICE_LEVELS; y++) {
            for (int x = 0; x < TIME_BUCKETS; x++) {
                int volume = heatmap[y][x];

                if (volume == 0) {
                    continue;
                }

                int intensity = Math.min(255, volume * 4);
                g.setColor(new Color(intensity, intensity / 2, 0));

                int px = (int) (x * cellW);
                int py = (int) ((PRICE_LEVELS - 1 - y) * cellH);

                g.fillRect(px, py, Math.max(1, (int) cellW + 1), Math.max(1, (int) cellH + 1));
            }
        }

        g.setColor(Color.BLACK);
        g.drawString("Synthetic liquidity heatmap", 20, 30);
    }

    private void updateDebugInfo() {
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;

        int copiedIntsPerTick = PRICE_LEVELS * (TIME_BUCKETS - 1);
        int clearedCellsPerTick = PRICE_LEVELS;
        int matrixChecksPerPaint = PRICE_LEVELS * TIME_BUCKETS;
        int requestedTicksPerSecond = 1_000 / TIMER_MS;

        StringBuilder sb = new StringBuilder();

        sb.append("=== PERFORMANCE ===\n");
        sb.append("FPS                : ").append(String.format("%.0f", fps)).append('\n');
        sb.append("Generate ms        : ")
                .append(String.format("%.3f", Profiler.get(Profiler.EventType.GENERATE_DATA)))
                .append('\n');
        sb.append("Paint ms           : ")
                .append(String.format("%.3f", Profiler.get(Profiler.EventType.PAINT)))
                .append('\n');
        sb.append("Memory MB          : ").append(usedMb).append('\n');

        sb.append('\n');

        sb.append("=== LOAD ===\n");
        sb.append("Timer ms           : ").append(TIMER_MS).append('\n');
        sb.append("Requested ticks/sec: ").append(requestedTicksPerSecond).append('\n');
        sb.append("Random updates/tick: ").append(RANDOM_UPDATES_PER_TICK).append('\n');
        sb.append("Ticks generated    : ").append(tickCount).append('\n');

        sb.append('\n');

        sb.append("=== MATRIX ===\n");
        sb.append("Price levels       : ").append(PRICE_LEVELS).append('\n');
        sb.append("Time buckets       : ").append(TIME_BUCKETS).append('\n');
        sb.append("Cells              : ").append(PRICE_LEVELS * TIME_BUCKETS).append('\n');
        sb.append("Copied int/tick    : ").append(copiedIntsPerTick).append('\n');
        sb.append("Cleared cells/tick : ").append(clearedCellsPerTick).append('\n');
        sb.append("Checks/paint       : ").append(matrixChecksPerPaint).append('\n');

        sb.append('\n');

        sb.append("=== ESTIMATE ===\n");
        sb.append("Copied int/sec     : ").append(copiedIntsPerTick * requestedTicksPerSecond).append('\n');
        sb.append("Cleared cells/sec  : ").append(clearedCellsPerTick * requestedTicksPerSecond).append('\n');
        sb.append("Random updates/sec : ").append(RANDOM_UPDATES_PER_TICK * requestedTicksPerSecond).append('\n');

        infoFrame.updateText(sb.toString());
    }
}
