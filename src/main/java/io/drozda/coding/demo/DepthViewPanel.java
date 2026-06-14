package io.drozda.coding.demo;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

final class DepthViewPanel extends JPanel {
    private static final double MAX_BAR_WIDTH_RATIO = 0.36;

    private final MarketViewState state;

    DepthViewPanel(MarketViewState state) {
        this.state = state;
        setBackground(BookmapTheme.PANEL_BACKGROUND);
    }

    @Override
    protected void paintComponent(Graphics g) {
        // Swing paints components on the EDT. This method should only render
        // the latest prepared state, not calculate market data.
        super.paintComponent(g);

        // Work on a Graphics2D copy so local color/font/stroke changes stay local
        // to this component.
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            // Makes small ladder text easier to read.
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            draw(g2);
        } finally {
            // Releases the copied graphics context created above.
            g2.dispose();
        }
    }

    private void draw(Graphics2D g) {
        g.setColor(BookmapTheme.PANEL_BACKGROUND);
        g.fillRect(0, 0, getWidth(), getHeight());

        // Copy current depth quickly, then draw without holding the market lock.
        DepthSnapshot snapshot = snapshot();
        int maxVisibleVolume = maxVisibleVolume(snapshot);

        for (int level = 0; level < MarketConfig.PRICE_LEVELS; level++) {
            int y = PriceScale.levelToY(level, getHeight());
            int nextY = PriceScale.levelToY(level - 1, getHeight());
            int rowH = Math.max(1, nextY - y);
            int price = MarketConfig.levelToPrice(level);
            int bidVolume = snapshot.bidVolumes[level];
            int askVolume = snapshot.askVolumes[level];

            if (price == snapshot.referencePrice) {
                g.setColor(new Color(70, 98, 104));
                g.fillRect(0, y, getWidth(), rowH);
            }

            int bidW = (int) ((getWidth() * MAX_BAR_WIDTH_RATIO) * (bidVolume / (double) maxVisibleVolume));
            int askW = (int) ((getWidth() * MAX_BAR_WIDTH_RATIO) * (askVolume / (double) maxVisibleVolume));

            g.setColor(new Color(53, 191, 111, 160));
            g.fillRect(0, y, bidW, rowH);
            g.setColor(new Color(207, 60, 72, 160));
            g.fillRect(getWidth() - askW, y, askW, rowH);
        }

        drawPriceLabels(g, snapshot);

        drawHeader(g);
    }

    private DepthSnapshot snapshot() {
        DepthSnapshot snapshot = new DepthSnapshot();

        synchronized (state) {
            snapshot.referencePrice = state.referencePrice();

            for (int level = 0; level < MarketConfig.PRICE_LEVELS; level++) {
                snapshot.bidVolumes[level] = state.bidVolumeAt(level);
                snapshot.askVolumes[level] = state.askVolumeAt(level);
            }
        }

        return snapshot;
    }

    private void drawHeader(Graphics2D g) {
        g.setColor(BookmapTheme.PANEL_BACKGROUND);
        g.fillRect(0, 0, getWidth(), 30);
        g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        g.setColor(BookmapTheme.TEXT);
        g.drawString("COB", 14, 22);
        g.drawString("BID", 52, 22);
        g.drawString("ASK", 108, 22);
    }

    private int maxVisibleVolume(DepthSnapshot snapshot) {
        int maxVisibleVolume = 1;

        for (int level = 0; level < MarketConfig.PRICE_LEVELS; level++) {
            maxVisibleVolume = Math.max(maxVisibleVolume, snapshot.bidVolumes[level] + snapshot.askVolumes[level]);
        }

        return maxVisibleVolume;
    }

    private void drawPriceLabels(Graphics2D g, DepthSnapshot snapshot) {
        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));

        int horizontalLines = 12;
        for (int i = 0; i <= horizontalLines; i++) {
            int level = PriceScale.clampLevel(
                    MarketConfig.PRICE_LEVELS - 1 - (i * MarketConfig.PRICE_LEVELS / horizontalLines)
            );
            int y = PriceScale.levelToY(level, getHeight());
            int bidVolume = snapshot.bidVolumes[level];
            int askVolume = snapshot.askVolumes[level];

            g.setColor(BookmapTheme.GRID_MINOR);
            g.drawLine(0, y, getWidth(), y);

            g.setColor(BookmapTheme.MUTED_TEXT);
            g.drawString(PriceDisplay.formatInternalPrice(MarketConfig.levelToPrice(level)), 8, y + 4);
            g.setColor(BookmapTheme.BID);
            g.drawString(String.valueOf(bidVolume), 54, y + 4);
            g.setColor(BookmapTheme.ASK);
            g.drawString(String.valueOf(askVolume), 108, y + 4);
        }
    }

    private static final class DepthSnapshot {
        final int[] bidVolumes = new int[MarketConfig.PRICE_LEVELS];
        final int[] askVolumes = new int[MarketConfig.PRICE_LEVELS];
        int referencePrice;
    }
}
