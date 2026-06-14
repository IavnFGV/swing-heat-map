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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class HeatmapViewPanel extends JPanel {
    static final int PRICE_AXIS_W = 72;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final MarketViewState state;

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

        // All panels use the same state and the same PriceScale. That is what keeps
        // the heatmap price axis and the right-side depth ladder vertically aligned.
        synchronized (state) {
            drawHeatmap(g, plot);
            drawGrid(g, plot, priceAxis);
            drawMidLine(g, plot);
            drawPriceHistory(g, state.bestBidHistory, BookmapTheme.BID, plot);
            drawPriceHistory(g, state.bestAskHistory, BookmapTheme.ASK, plot);
        }
    }

    private void drawHeatmap(Graphics2D g, Rectangle plot) {
        // Paint by visible pixels instead of by the full data matrix.
        // With 2_000 x 5_000 buckets the raw matrix has 10M cells; the panel
        // usually has far fewer pixels than that.
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

                int volume = state.heatmap[priceLevel][bufferColumn];

                if (volume == 0) {
                    continue;
                }

                g.setColor(BookmapTheme.heatColor(volume));
                g.fillRect(plot.x + screenX, plot.y + screenY, 1, 1);
            }
        }
    }

    private void drawGrid(Graphics2D g, Rectangle plot, Rectangle priceAxis) {
        g.setColor(BookmapTheme.AXIS_BACKGROUND);
        g.fillRect(priceAxis.x, priceAxis.y, priceAxis.width, priceAxis.height);
        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));

        int verticalLines = 10;
        for (int i = 0; i <= verticalLines; i++) {
            int x = plot.x + (int) (plot.width * (i / (double) verticalLines));
            boolean major = i % 2 == 0;
            g.setColor(major ? BookmapTheme.GRID_MAJOR : BookmapTheme.GRID_MINOR);
            g.drawLine(x, plot.y, x, plot.y + plot.height);

            if (major) {
                long timestampMicros = state.visibleTimestampMicros(i * plot.width / verticalLines, plot.width);
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
                g.drawString(String.valueOf(MarketConfig.levelToPrice(level)), priceAxis.x + 8, y + 4);
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

    private void drawPriceHistory(Graphics2D g, int[] history, Color color, Rectangle plot) {
        g.setColor(color);
        g.setStroke(new BasicStroke(1.6f));

        for (int x = 1; x < plot.width; x++) {
            int prevColumn = state.visibleScreenXToBufferColumn(x - 1, plot.width);
            int currColumn = state.visibleScreenXToBufferColumn(x, plot.width);

            if (prevColumn == -1 || currColumn == -1) {
                continue;
            }

            int prevPrice = history[prevColumn];
            int currPrice = history[currColumn];

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

    private void drawMidLine(Graphics2D g, Rectangle plot) {
        int referencePrice = state.referencePrice();
        int midY = plot.y + PriceScale.priceToY(referencePrice, plot.height);

        float[] dash = {8f, 8f};
        Stroke oldStroke = g.getStroke();
        g.setColor(Color.CYAN);
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
        g.drawLine(plot.x, midY, plot.x + plot.width, midY);
        g.setStroke(oldStroke);
        g.drawString("ref: " + referencePrice, plot.x + 20, midY - 5);
    }
}
