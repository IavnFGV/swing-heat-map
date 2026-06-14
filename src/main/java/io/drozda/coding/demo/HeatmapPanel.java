package io.drozda.coding.demo;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public class HeatmapPanel extends JPanel {

    private final int[][] heatmap =
            new int[MarketConfig.PRICE_LEVELS][MarketConfig.TIME_BUCKETS];

    private final int[] bestBidHistory = new int[MarketConfig.TIME_BUCKETS];
    private final int[] bestAskHistory = new int[MarketConfig.TIME_BUCKETS];

    private final EventGenerator eventGenerator = new EventGenerator();
    private final OrderBook orderBook = new OrderBook();
    private final InfoFrame infoFrame;

    private final Object lock = new Object();

    private volatile boolean running = true;

    private ScrollMode scrollMode = ScrollMode.SHIFT_COPY;
    private int currentColumn = MarketConfig.TIME_BUCKETS - 1;

    private int eventsPerTick = MarketConfig.EVENTS_PER_TICK;

    private long fpsCounterStart = System.nanoTime();
    private long fpsFrameCount = 0;
    private double fps = 0.0;

    private long totalEvents = 0;
    private long totalTradedVolume = 0;

    private double lastGenerateMs = 0.0;
    private double lastPaintMs = 0.0;

    public HeatmapPanel(InfoFrame infoFrame) {
        this.infoFrame = infoFrame;

        Arrays.fill(bestBidHistory, -1);
        Arrays.fill(bestAskHistory, -1);

        setFocusable(true);
        setupKeys();

        startWorkerThread();
        startUiTimer();
    }

    private void startWorkerThread() {
        Thread worker = new Thread(() -> {
            while (running) {
                long start = System.nanoTime();

                    generateFakeData();

                lastGenerateMs = (System.nanoTime() - start) / 1_000_000.0;
            }
        }, "market-data-worker");

        worker.setDaemon(true);
        worker.start();
    }

    private void startUiTimer() {
        Timer timer = new Timer(16, e -> {
            fpsFrameCount++;

            long now = System.nanoTime();

            if (now - fpsCounterStart >= 1_000_000_000L) {
                fps = fpsFrameCount;
                fpsFrameCount = 0;
                fpsCounterStart = now;
            }

            updateDebugInfo();
            repaint();
        });

        timer.start();
    }

    private void generateFakeData() {
        if (scrollMode == ScrollMode.SHIFT_COPY) {
            generateWithShiftCopy();
        } else {
            generateWithCircularBuffer();
        }
    }

    private void generateWithShiftCopy() {
        Profiler.measure(Profiler.EventType.SCROLL_COPY, () -> {
            for (int y = 0; y < MarketConfig.PRICE_LEVELS; y++) {
                System.arraycopy(
                        heatmap[y],
                        1,
                        heatmap[y],
                        0,
                        MarketConfig.TIME_BUCKETS - 1
                );

                heatmap[y][MarketConfig.TIME_BUCKETS - 1] = 0;
            }

            System.arraycopy(bestBidHistory, 1, bestBidHistory, 0, MarketConfig.TIME_BUCKETS - 1);
            System.arraycopy(bestAskHistory, 1, bestAskHistory, 0, MarketConfig.TIME_BUCKETS - 1);
        });

        Profiler.measure(Profiler.EventType.APPLY_EVENTS, this::applyEvents);

        Profiler.measure(Profiler.EventType.GEN_DATA, () -> {
            for (int priceLevel = 0; priceLevel < MarketConfig.PRICE_LEVELS; priceLevel++) {
                heatmap[priceLevel][MarketConfig.TIME_BUCKETS - 1] =
                        orderBook.totalVolumeAt(priceLevel);
            }

            bestBidHistory[MarketConfig.TIME_BUCKETS - 1] = orderBook.bestBidPrice();
            bestAskHistory[MarketConfig.TIME_BUCKETS - 1] = orderBook.bestAskPrice();

            currentColumn = MarketConfig.TIME_BUCKETS - 1;
        });
    }

    private void generateWithCircularBuffer() {
        Profiler.measure(Profiler.EventType.SCROLL_COPY, () -> {
            currentColumn = (currentColumn + 1) % MarketConfig.TIME_BUCKETS;

            for (int priceLevel = 0; priceLevel < MarketConfig.PRICE_LEVELS; priceLevel++) {
                heatmap[priceLevel][currentColumn] = 0;
            }

            bestBidHistory[currentColumn] = -1;
            bestAskHistory[currentColumn] = -1;
        });

        Profiler.measure(Profiler.EventType.APPLY_EVENTS, this::applyEvents);

        Profiler.measure(Profiler.EventType.GEN_DATA, () -> {
            for (int priceLevel = 0; priceLevel < MarketConfig.PRICE_LEVELS; priceLevel++) {
                heatmap[priceLevel][currentColumn] =
                        orderBook.totalVolumeAt(priceLevel);
            }

            bestBidHistory[currentColumn] = orderBook.bestBidPrice();
            bestAskHistory[currentColumn] = orderBook.bestAskPrice();
        });
    }

    private void applyEvents() {
        for (int i = 0; i < eventsPerTick; i++) {
            BookEvent event = eventGenerator.nextEvent();
            orderBook.apply(event);

            totalEvents++;

            if (event.type() == EventType.TRADE) {
                totalTradedVolume += event.volume();
            }
        }
    }

    private int screenXToBufferColumn(int screenX) {
        if (scrollMode == ScrollMode.SHIFT_COPY) {
            return screenX;
        }

        return (currentColumn + 1 + screenX) % MarketConfig.TIME_BUCKETS;
    }

    @Override
    protected void paintComponent(Graphics g) {
        long paintStart = System.nanoTime();

        super.paintComponent(g);

//        synchronized (lock) {
            drawHeatmap(g);
//        }

        lastPaintMs = (System.nanoTime() - paintStart) / 1_000_000.0;
    }

    private void drawHeatmap(Graphics g) {
        int w = getWidth();
        int h = getHeight();

        double cellW = w / (double) MarketConfig.TIME_BUCKETS;
        double cellH = h / (double) MarketConfig.PRICE_LEVELS;

        for (int y = 0; y < MarketConfig.PRICE_LEVELS; y++) {
            for (int x = 0; x < MarketConfig.TIME_BUCKETS; x++) {
                int bufferColumn = screenXToBufferColumn(x);
                int volume = heatmap[y][bufferColumn];

                if (volume == 0) {
                    continue;
                }

                int intensity = (int) Math.min(255, Math.log1p(volume) * 35);
                g.setColor(new Color(intensity, intensity / 2, 0));

                int px = (int) (x * cellW);
                int py = (int) ((MarketConfig.PRICE_LEVELS - 1 - y) * cellH);

                g.fillRect(
                        px,
                        py,
                        Math.max(1, (int) cellW + 1),
                        Math.max(1, (int) cellH + 1)
                );
            }
        }

        drawMidLine(g, w, cellH);
        drawPriceHistory(g, bestBidHistory, Color.BLUE, cellW, cellH);
        drawPriceHistory(g, bestAskHistory, Color.RED, cellW, cellH);
    }

    private void drawPriceHistory(Graphics g, int[] history, Color color, double cellW, double cellH) {
        g.setColor(color);

        for (int x = 1; x < MarketConfig.TIME_BUCKETS; x++) {
            int prevColumn = screenXToBufferColumn(x - 1);
            int currColumn = screenXToBufferColumn(x);

            int prevPrice = history[prevColumn];
            int currPrice = history[currColumn];

            if (prevPrice == -1 || currPrice == -1) {
                continue;
            }

            int prevLevel = MarketConfig.priceToLevel(prevPrice);
            int currLevel = MarketConfig.priceToLevel(currPrice);

            if (!isValidPriceLevel(prevLevel) || !isValidPriceLevel(currLevel)) {
                continue;
            }

            int x1 = (int) ((x - 1) * cellW);
            int x2 = (int) (x * cellW);

            int y1 = (int) ((MarketConfig.PRICE_LEVELS - 1 - prevLevel) * cellH);
            int y2 = (int) ((MarketConfig.PRICE_LEVELS - 1 - currLevel) * cellH);

            g.drawLine(x1, y1, x2, y2);
        }
    }

    private void drawMidLine(Graphics g, int width, double cellH) {
        int midPriceLevel = MarketConfig.priceToLevel(MarketConfig.MID_PRICE);
        int midY = (int) ((MarketConfig.PRICE_LEVELS - 1 - midPriceLevel) * cellH);

        g.setColor(Color.CYAN);
        g.drawLine(0, midY, width, midY);
        g.drawString("mid: " + MarketConfig.MID_PRICE, 20, midY - 5);
    }

    private boolean isValidPriceLevel(int priceLevel) {
        return priceLevel >= 0 && priceLevel < MarketConfig.PRICE_LEVELS;
    }

    private void updateDebugInfo() {
        int bestBid;
        int bestAsk;
        int columnSnapshot;
        long eventsSnapshot;
        long volumeSnapshot;

//        synchronized (lock) {
            bestBid = orderBook.bestBidPrice();
            bestAsk = orderBook.bestAskPrice();
            columnSnapshot = currentColumn;
            eventsSnapshot = totalEvents;
            volumeSnapshot = totalTradedVolume;
//        }

        Runtime runtime = Runtime.getRuntime();

        long usedMb =
                (runtime.totalMemory() - runtime.freeMemory())
                        / 1024 / 1024;

        StringBuilder sb = new StringBuilder();

        sb.append("=== PERFORMANCE ===\n");
        sb.append("Mode               : ").append(scrollMode).append('\n');
        sb.append("FPS                : ").append(String.format("%.0f", fps)).append('\n');
        sb.append("Generate ms        : ").append(String.format("%.3f", lastGenerateMs)).append('\n');
        sb.append("Paint ms           : ").append(String.format("%.3f", lastPaintMs)).append('\n');
        sb.append("Memory MB          : ").append(usedMb).append('\n');

        sb.append('\n');

        sb.append("=== LOAD ===\n");
        sb.append("Events/tick        : ").append(eventsPerTick).append('\n');
        sb.append("Approx events/sec  : ").append(eventsPerTick * 60L).append('\n');
        sb.append("Total events       : ").append(eventsSnapshot).append('\n');
        sb.append("Total traded volume: ").append(volumeSnapshot).append('\n');

        sb.append('\n');

        sb.append("=== MARKET ===\n");
        sb.append("Best bid           : ").append(bestBid).append('\n');
        sb.append("Best ask           : ").append(bestAsk).append('\n');

        if (bestBid != -1 && bestAsk != -1) {
            sb.append("Spread             : ").append(bestAsk - bestBid).append('\n');
            sb.append("Mid                : ").append((bestBid + bestAsk) / 2.0).append('\n');
        }

        sb.append('\n');

        sb.append("=== BUFFER ===\n");
        sb.append("Current column     : ").append(columnSnapshot).append('\n');
        sb.append("Price levels       : ").append(MarketConfig.PRICE_LEVELS).append('\n');
        sb.append("Time buckets       : ").append(MarketConfig.TIME_BUCKETS).append('\n');

        sb.append('\n');

        sb.append("=== PROFILER ===\n");
        sb.append(Profiler.report());

        sb.append('\n');

        sb.append("=== KEYS ===\n");
        sb.append("1  -> SHIFT_COPY\n");
        sb.append("2  -> CIRCULAR_BUFFER\n");
        sb.append("+  -> increase load\n");
        sb.append("-  -> decrease load\n");

        infoFrame.updateText(sb.toString());
    }

    private void setupKeys() {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('1'), "shift");
        getActionMap().put("shift", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                scrollMode = ScrollMode.SHIFT_COPY;
            }
        });

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('2'), "circular");
        getActionMap().put("circular", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                scrollMode = ScrollMode.CIRCULAR_BUFFER;
            }
        });

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('+'), "more");
        getActionMap().put("more", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                eventsPerTick *= 2;
            }
        });

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('-'), "less");
        getActionMap().put("less", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                eventsPerTick = Math.max(1, eventsPerTick / 2);
            }
        });
    }
}