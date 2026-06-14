package io.drozda.coding.demo;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;

final class ActivityViewPanel extends JPanel {
    private final MarketViewState state;

    ActivityViewPanel(MarketViewState state) {
        this.state = state;
        setBackground(BookmapTheme.PANEL_BACKGROUND);
    }

    @Override
    protected void paintComponent(Graphics g) {
        // This lower panel shares time/history with the heatmap, but its Y axis
        // is volume/activity instead of price.
        super.paintComponent(g);

        // Copy the Graphics object so changes made here do not affect siblings.
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            // Anti-aliased text keeps the small labels readable.
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            draw(g2);
        } finally {
            // Dispose only the copied Graphics2D context.
            g2.dispose();
        }
    }

    private void draw(Graphics2D g) {
        g.setColor(BookmapTheme.PANEL_BACKGROUND);
        g.fillRect(0, 0, getWidth(), getHeight());

        g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        g.setColor(BookmapTheme.TEXT);
        g.drawString("Trades / CVD", 14, 22);

        Rectangle plot = new Rectangle(8, 34, getWidth() - 16, getHeight() - 44);
        Rectangle cvdPlot = new Rectangle(plot.x, plot.y, plot.width, Math.max(1, plot.height / 2 - 4));
        Rectangle volumePlot = new Rectangle(plot.x, plot.y + plot.height / 2 + 6, plot.width, Math.max(1, plot.height / 2 - 6));

        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        g.setColor(BookmapTheme.MUTED_TEXT);
        g.drawString("CVD", cvdPlot.x + 4, cvdPlot.y + 12);
        g.drawString("Volume / delta", volumePlot.x + 4, volumePlot.y + 12);

        g.setColor(BookmapTheme.GRID_MINOR);
        for (int i = 1; i < 4; i++) {
            int y = volumePlot.y + i * volumePlot.height / 4;
            g.drawLine(volumePlot.x, y, volumePlot.x + volumePlot.width, y);
        }

        synchronized (state) {
            drawVolumeBars(g, volumePlot);
            drawZeroLine(g, volumePlot);
            drawDeltaBars(g, volumePlot);
            drawCvdLine(g, cvdPlot);
        }
    }

    private void drawVolumeBars(Graphics2D g, Rectangle plot) {
        int max = 1;
        for (int screenX = 0; screenX < plot.width; screenX++) {
            max = Math.max(max, state.visibleTradeVolumeBucket(screenX, plot.width));
        }

        double logMax = Math.log1p(max);

        for (int screenX = 0; screenX < plot.width; screenX++) {
            int volume = state.visibleTradeVolumeBucket(screenX, plot.width);
            if (volume == 0) {
                continue;
            }

            // Log scaling keeps one fresh large trade from constantly rescaling
            // the whole activity chart and causing visible flicker.
            int barH = Math.max(1, (int) (plot.height * (Math.log1p(volume) / logMax)));
            int px = plot.x + screenX;
            int py = plot.y + plot.height - barH;

            g.setColor(BookmapTheme.TRADE);
            g.drawLine(px, plot.y + plot.height, px, py);
        }
    }

    private void drawZeroLine(Graphics2D g, Rectangle plot) {
        int zeroY = plot.y + plot.height / 2;
        g.setColor(BookmapTheme.GRID_MAJOR);
        g.drawLine(plot.x, zeroY, plot.x + plot.width, zeroY);
    }

    private void drawDeltaBars(Graphics2D g, Rectangle plot) {
        int maxAbsDelta = 1;
        for (int screenX = 0; screenX < plot.width; screenX++) {
            maxAbsDelta = Math.max(maxAbsDelta, Math.abs(state.visibleTradeDeltaBucket(screenX, plot.width)));
        }

        int zeroY = plot.y + plot.height / 2;
        int maxH = Math.max(1, plot.height / 3);

        for (int screenX = 0; screenX < plot.width; screenX++) {
            int delta = state.visibleTradeDeltaBucket(screenX, plot.width);
            if (delta == 0) {
                continue;
            }

            int barH = Math.max(1, (int) (maxH * (Math.abs(delta) / (double) maxAbsDelta)));
            int x = plot.x + screenX;

            if (delta > 0) {
                g.setColor(BookmapTheme.BID);
                g.drawLine(x, zeroY, x, zeroY - barH);
            } else {
                g.setColor(BookmapTheme.ASK);
                g.drawLine(x, zeroY, x, zeroY + barH);
            }
        }
    }

    private void drawCvdLine(Graphics2D g, Rectangle plot) {
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;

        for (int screenX = 0; screenX < plot.width; screenX++) {
            long cvd = state.visibleCvd(screenX, plot.width);
            min = Math.min(min, cvd);
            max = Math.max(max, cvd);
        }

        if (min == Long.MAX_VALUE || max == Long.MIN_VALUE || min == max) {
            return;
        }

        drawCvdAxis(g, plot, min, max);

        Stroke oldStroke = g.getStroke();
        g.setStroke(new BasicStroke(1.6f));
        g.setColor(BookmapTheme.TEXT);

        int prevX = plot.x;
        int prevY = cvdToY(state.visibleCvd(0, plot.width), min, max, plot);

        for (int screenX = 1; screenX < plot.width; screenX++) {
            int x = plot.x + screenX;
            int y = cvdToY(state.visibleCvd(screenX, plot.width), min, max, plot);
            g.drawLine(prevX, prevY, x, y);
            prevX = x;
            prevY = y;
        }

        g.setStroke(oldStroke);
    }

    private void drawCvdAxis(Graphics2D g, Rectangle plot, long min, long max) {
        g.setFont(getFont().deriveFont(Font.PLAIN, 10f));
        g.setColor(BookmapTheme.MUTED_TEXT);
        g.drawString(formatSigned(max), plot.x + plot.width - 52, plot.y + 10);
        g.drawString(formatSigned(min), plot.x + plot.width - 52, plot.y + plot.height - 3);

        if (min < 0 && max > 0) {
            int zeroY = cvdToY(0, min, max, plot);
            g.setColor(BookmapTheme.GRID_MAJOR);
            g.drawLine(plot.x, zeroY, plot.x + plot.width, zeroY);
            g.setColor(BookmapTheme.MUTED_TEXT);
            g.drawString("0", plot.x + plot.width - 52, zeroY - 2);
        }

        long last = state.visibleCvd(plot.width - 1, plot.width);
        int lastY = cvdToY(last, min, max, plot);
        g.setColor(BookmapTheme.TEXT);
        g.drawString(formatSigned(last), plot.x + 42, lastY - 2);
    }

    private String formatSigned(long value) {
        if (Math.abs(value) >= 1_000_000) {
            return String.format("%+.1fm", value / 1_000_000.0);
        }
        if (Math.abs(value) >= 1_000) {
            return String.format("%+.1fk", value / 1_000.0);
        }
        return String.format("%+d", value);
    }

    private int cvdToY(long cvd, long min, long max, Rectangle plot) {
        double t = (cvd - min) / (double) (max - min);
        return plot.y + plot.height - 1 - (int) (t * (plot.height - 1));
    }
}
