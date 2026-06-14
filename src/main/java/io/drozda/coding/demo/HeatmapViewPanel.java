package io.drozda.coding.demo;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class HeatmapViewPanel extends JPanel {
    static final int PRICE_AXIS_W = 72;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final MarketViewState state;
    private BufferedImage heatmapImage;

    HeatmapViewPanel(MarketViewState state) {
        this.state = state;
        setBackground(BookmapTheme.BACKGROUND);
    }

    @Override
    protected void paintComponent(Graphics g) {
        // Swing calls this method on the EDT (Event Dispatch Thread).
        // Always call super first so Swing can clear the component correctly.
        super.paintComponent(g);

        // Graphics is shared by Swing during painting. create() gives us a local copy
        // so stroke/font/color changes inside this panel do not leak into other painting code.
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            // Rendering hints trade a little CPU for smoother text and lines.
            // They do not change the model; only the quality of drawing.
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            draw(g2);
        } finally {
            // dispose() releases this local Graphics copy. It does not close the window
            // or destroy Swing's original Graphics object.
            g2.dispose();
        }
    }

    private void draw(Graphics2D g) {
        if (getWidth() <= PRICE_AXIS_W || getHeight() <= 0) {
            return;
        }

        Rectangle plot = new Rectangle(0, 0, getWidth() - PRICE_AXIS_W, getHeight());
        Rectangle priceAxis = new Rectangle(plot.width, 0, PRICE_AXIS_W, getHeight());

        g.setColor(new Color(10, 22, 26));
        g.fillRect(plot.x, plot.y, plot.width, plot.height);

        // Copy the visible state quickly, then release the market lock before
        // doing expensive Swing drawing. This is the first step toward a proper
        // published render frame / double-buffer model.
        final HeatmapSnapshot[] snapshot = new HeatmapSnapshot[1];
        Profiler.measure(Profiler.EventType.HEATMAP_SNAPSHOT, () -> snapshot[0] = snapshot(plot));

        Profiler.measure(Profiler.EventType.HEATMAP_PAINT, () -> {
            drawHeatmap(g, plot, snapshot[0]);
            drawGrid(g, plot, priceAxis, snapshot[0]);
            drawMidLine(g, plot, snapshot[0]);
            drawPriceHistory(g, snapshot[0].bestBidPrices, BookmapTheme.BID, plot);
            drawPriceHistory(g, snapshot[0].bestAskPrices, BookmapTheme.ASK, plot);
        });
    }

    private HeatmapSnapshot snapshot(Rectangle plot) {
        HeatmapSnapshot snapshot = new HeatmapSnapshot(plot.width, plot.height);

        synchronized (state) {
            snapshot.referencePrice = state.referencePrice();

            for (int screenY = 0; screenY < plot.height; screenY++) {
                int priceLevel = PriceScale.clampLevel(
                        MarketConfig.PRICE_LEVELS - 1
                                - (int) (screenY * (MarketConfig.PRICE_LEVELS / (double) plot.height))
                );

                for (int screenX = 0; screenX < plot.width; screenX++) {
                    int bufferColumn = state.visibleScreenXToBufferColumn(screenX, plot.width);
                    if (bufferColumn == -1) {
                        continue;
                    }

                    snapshot.volumes[screenY * plot.width + screenX] = state.heatmap[priceLevel][bufferColumn];
                }
            }

            for (int x = 0; x < plot.width; x++) {
                int column = state.visibleScreenXToBufferColumn(x, plot.width);
                if (column == -1) {
                    snapshot.bestBidPrices[x] = -1;
                    snapshot.bestAskPrices[x] = -1;
                } else {
                    snapshot.bestBidPrices[x] = state.bestBidHistory[column];
                    snapshot.bestAskPrices[x] = state.bestAskHistory[column];
                }
            }

            for (int i = 0; i <= HeatmapSnapshot.GRID_LINES; i++) {
                int x = i * plot.width / HeatmapSnapshot.GRID_LINES;
                snapshot.gridTimestamps[i] = state.visibleTimestampMicros(x, plot.width);
            }
        }

        return snapshot;
    }

    private void drawHeatmap(Graphics2D g, Rectangle plot, HeatmapSnapshot snapshot) {
        ensureHeatmapImage(plot.width, plot.height);

        int[] pixels = ((DataBufferInt) heatmapImage.getRaster().getDataBuffer()).getData();
        int background = new Color(10, 22, 26).getRGB();

        for (int screenY = 0; screenY < plot.height; screenY++) {
            for (int screenX = 0; screenX < plot.width; screenX++) {
                int volume = snapshot.volumes[screenY * plot.width + screenX];

                pixels[screenY * plot.width + screenX] = volume == 0
                        ? background
                        : BookmapTheme.heatRgb(volume);
            }
        }

        g.drawImage(heatmapImage, plot.x, plot.y, null);
    }

    private void ensureHeatmapImage(int width, int height) {
        if (heatmapImage == null || heatmapImage.getWidth() != width || heatmapImage.getHeight() != height) {
            heatmapImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        }
    }

    private void drawGrid(Graphics2D g, Rectangle plot, Rectangle priceAxis, HeatmapSnapshot snapshot) {
        g.setColor(BookmapTheme.AXIS_BACKGROUND);
        g.fillRect(priceAxis.x, priceAxis.y, priceAxis.width, priceAxis.height);
        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));

        int verticalLines = HeatmapSnapshot.GRID_LINES;
        for (int i = 0; i <= verticalLines; i++) {
            int x = plot.x + (int) (plot.width * (i / (double) verticalLines));
            boolean major = i % 2 == 0;
            g.setColor(major ? BookmapTheme.GRID_MAJOR : BookmapTheme.GRID_MINOR);
            g.drawLine(x, plot.y, x, plot.y + plot.height);

            if (major) {
                long timestampMicros = snapshot.gridTimestamps[i];
                g.setColor(BookmapTheme.MUTED_TEXT);
                g.drawString(formatTimeLabel(timestampMicros), x + 4, plot.y + plot.height - 8);
            }
        }

        int horizontalLines = 12;
        for (int i = 0; i <= horizontalLines; i++) {
            int y = plot.y + (int) (plot.height * (i / (double) horizontalLines));
            boolean major = i % 2 == 0;
            g.setColor(major ? BookmapTheme.GRID_MAJOR : BookmapTheme.GRID_MINOR);
            g.drawLine(plot.x, y, plot.x + plot.width, y);

            if (major) {
                int level = PriceScale.clampLevel(
                        MarketConfig.PRICE_LEVELS - 1 - (i * MarketConfig.PRICE_LEVELS / horizontalLines)
                );
                g.setColor(BookmapTheme.TEXT);
                g.drawString(PriceDisplay.formatInternalPrice(MarketConfig.levelToPrice(level)), priceAxis.x + 8, y + 4);
            }
        }
    }

    private String formatTimeLabel(long timestampMicros) {
        if (timestampMicros > 1_000_000_000_000_000L) {
            long epochSecond = timestampMicros / 1_000_000L;
            long nanoAdjustment = (timestampMicros % 1_000_000L) * 1_000L;
            return TIME_FORMAT.format(Instant.ofEpochSecond(epochSecond, nanoAdjustment));
        }

        return "--:--:--";
    }

    private void drawPriceHistory(Graphics2D g, int[] visiblePrices, Color color, Rectangle plot) {
        g.setColor(color);
        g.setStroke(new BasicStroke(1.6f));

        for (int x = 1; x < plot.width; x++) {
            int prevPrice = visiblePrices[x - 1];
            int currPrice = visiblePrices[x];

            if (prevPrice == -1 || currPrice == -1) {
                continue;
            }

            int prevLevel = MarketConfig.priceToLevel(prevPrice);
            int currLevel = MarketConfig.priceToLevel(currPrice);

            if (!PriceScale.isValidLevel(prevLevel) || !PriceScale.isValidLevel(currLevel)) {
                continue;
            }

            int x1 = plot.x + x - 1;
            int x2 = plot.x + x;
            int y1 = plot.y + PriceScale.levelToY(prevLevel, plot.height);
            int y2 = plot.y + PriceScale.levelToY(currLevel, plot.height);

            g.drawLine(x1, y1, x2, y2);
        }
    }

    private void drawMidLine(Graphics2D g, Rectangle plot, HeatmapSnapshot snapshot) {
        int referencePrice = snapshot.referencePrice;
        int midY = plot.y + PriceScale.priceToY(referencePrice, plot.height);

        float[] dash = {8f, 8f};
        Stroke oldStroke = g.getStroke();
        g.setColor(Color.CYAN);
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
        g.drawLine(plot.x, midY, plot.x + plot.width, midY);
        g.setStroke(oldStroke);
        g.drawString("ref: " + PriceDisplay.formatInternalPrice(referencePrice), plot.x + 20, midY - 5);
    }

    private static final class HeatmapSnapshot {
        static final int GRID_LINES = 10;

        final int[] volumes;
        final int[] bestBidPrices;
        final int[] bestAskPrices;
        final long[] gridTimestamps = new long[GRID_LINES + 1];
        int referencePrice;

        HeatmapSnapshot(int width, int height) {
            volumes = new int[Math.max(0, width * height)];
            bestBidPrices = new int[Math.max(0, width)];
            bestAskPrices = new int[Math.max(0, width)];
        }
    }
}
