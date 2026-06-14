package io.drozda.coding.demo;

import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.nio.file.Files;
import java.nio.file.Path;

public class HeatmapPanel extends JPanel {
    private static final int HEADER_H = 42;
    private static final int DEPTH_W = 170;
    private static final int ACTIVITY_H = 118;
    private static final int MARKET_TICK_MS = 20;

    private final MarketViewState state = new MarketViewState();
    private final EventGenerator eventGenerator = createEventGenerator();
    private final InfoFrame infoFrame;

    private final MarketHeaderPanel headerPanel = new MarketHeaderPanel(state);
    private final HeatmapViewPanel heatmapViewPanel = new HeatmapViewPanel(state);
    private final DepthViewPanel depthViewPanel = new DepthViewPanel(state);
    private final ActivityViewPanel activityViewPanel = new ActivityViewPanel(state);

    private volatile boolean running = true;

    private long fpsCounterStart = System.nanoTime();
    private long fpsFrameCount = 0;
    private double fps = 0.0;

    private double lastGenerateMs = 0.0;

    private static EventGenerator createEventGenerator() {
        Path csvPath = Path.of("BTCUSDT-aggTrades-2026-06-12.csv");
        if (Files.exists(csvPath)) {
            return new HistoricalTradeBookGenerator(csvPath);
        }

        return new EventGenerator();
    }

    public HeatmapPanel(InfoFrame infoFrame) {
        this.infoFrame = infoFrame;

        setFocusable(true);
        setLayout(new BorderLayout(8, 8));
        setBackground(BookmapTheme.BACKGROUND);

        headerPanel.setPreferredSize(new Dimension(0, HEADER_H));
        depthViewPanel.setPreferredSize(new Dimension(DEPTH_W, 0));
        activityViewPanel.setPreferredSize(new Dimension(0, ACTIVITY_H));

        JPanel chartRow = new JPanel(new BorderLayout(8, 0));
        chartRow.setOpaque(false);
        chartRow.add(heatmapViewPanel, BorderLayout.CENTER);
        chartRow.add(depthViewPanel, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setOpaque(false);
        content.add(chartRow, BorderLayout.CENTER);
        content.add(activityViewPanel, BorderLayout.SOUTH);

        add(headerPanel, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);

        setupKeys();
        setupResizeRepaint();
        startWorkerThread();
        startUiTimer();
    }

    private void setupResizeRepaint() {
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                revalidate();
                repaintAllViews();
                updateDebugInfo();
            }
        });
    }

    private void startWorkerThread() {
        Thread worker = new Thread(() -> {
            while (running) {
                long start = System.nanoTime();

                state.generate(eventGenerator);

                lastGenerateMs = (System.nanoTime() - start) / 1_000_000.0;

                long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
                long sleepMs = Math.max(1, MARKET_TICK_MS - elapsedMs);

                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running = false;
                }
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

            // repaint() schedules paintComponent() calls on the EDT.
            // The panels render the same MarketViewState, so axes stay in sync.
            repaintAllViews();
        });

        timer.start();
    }

    private void repaintAllViews() {
        headerPanel.repaint();
        heatmapViewPanel.repaint();
        depthViewPanel.repaint();
        activityViewPanel.repaint();
    }

    private void updateDebugInfo() {
        int bestBid;
        int bestAsk;
        int columnSnapshot;
        long eventsSnapshot;
        long volumeSnapshot;
        int eventsPerTick;
        ScrollMode scrollMode;
        int timeScaleMs;
        int visibleRangeSeconds;

        synchronized (state) {
            bestBid = state.bestBidPrice();
            bestAsk = state.bestAskPrice();
            columnSnapshot = state.currentColumn();
            eventsSnapshot = state.totalEvents();
            volumeSnapshot = state.totalTradedVolume();
            eventsPerTick = state.eventsPerTick();
            scrollMode = state.scrollMode();
            timeScaleMs = state.timeScaleMs();
            visibleRangeSeconds = Math.max(0, (heatmapViewPanel.getWidth() - HeatmapViewPanel.PRICE_AXIS_W)
                    * timeScaleMs / 1_000);
        }

        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;

        StringBuilder sb = new StringBuilder();

        sb.append("=== PERFORMANCE ===\n");
        sb.append("Mode               : ").append(scrollMode).append('\n');
        sb.append("FPS                : ").append(String.format("%.0f", fps)).append('\n');
        sb.append("Generate ms        : ").append(String.format("%.3f", lastGenerateMs)).append('\n');
        sb.append("Memory MB          : ").append(usedMb).append('\n');

        sb.append('\n');

        sb.append("=== VIEW ===\n");
        sb.append("Raw bucket         : ").append(MarketViewState.RAW_SAMPLE_MS).append(" ms/column\n");
        sb.append("Time scale         : ").append(timeScaleMs).append(" ms/screen px\n");
        sb.append("Visible range      : ").append(visibleRangeSeconds).append(" sec\n");
        sb.append("Shared price scale : Heatmap + COB\n");

        sb.append('\n');

        sb.append("=== LOAD ===\n");
        sb.append("Events/tick        : ").append(eventsPerTick).append('\n');
        sb.append("Approx events/sec  : ").append(eventsPerTick * 60L).append('\n');
        sb.append("Total events       : ").append(eventsSnapshot).append('\n');
        sb.append("Total traded volume: ").append(volumeSnapshot).append('\n');

        sb.append('\n');

        sb.append("=== MARKET ===\n");
        sb.append("Best bid           : ").append(formatPrice(bestBid)).append('\n');
        sb.append("Best ask           : ").append(formatPrice(bestAsk)).append('\n');

        if (bestBid != -1 && bestAsk != -1) {
            sb.append("Spread ticks       : ").append(bestAsk - bestBid).append('\n');
            sb.append("Mid                : ").append(formatPrice((bestBid + bestAsk) / 2)).append('\n');
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
        sb.append("[  -> zoom out time scale\n");
        sb.append("]  -> zoom in time scale\n");
        sb.append("+  -> increase load\n");
        sb.append("-  -> decrease load\n");

        infoFrame.updateText(sb.toString());
    }

    private String formatPrice(int price) {
        if (price == -1) {
            return "-1";
        }

        return PriceDisplay.formatInternalPrice(price);
    }

    private void setupKeys() {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('1'), "shift");
        getActionMap().put("shift", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                synchronized (state) {
                    state.setScrollMode(ScrollMode.SHIFT_COPY);
                }
            }
        });

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('2'), "circular");
        getActionMap().put("circular", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                synchronized (state) {
                    state.setScrollMode(ScrollMode.CIRCULAR_BUFFER);
                }
            }
        });

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('+'), "more");
        getActionMap().put("more", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                synchronized (state) {
                    state.increaseLoad();
                }
            }
        });

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('-'), "less");
        getActionMap().put("less", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                synchronized (state) {
                    state.decreaseLoad();
                }
            }
        });

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('['), "zoomOut");
        getActionMap().put("zoomOut", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                synchronized (state) {
                    state.zoomOut();
                }
            }
        });

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(']'), "zoomIn");
        getActionMap().put("zoomIn", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                synchronized (state) {
                    state.zoomIn();
                }
            }
        });
    }
}
