package io.drozda.coding.demo;

import javax.swing.JPanel;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;

final class MarketHeaderPanel extends JPanel {
    private final MarketViewState state;

    MarketHeaderPanel(MarketViewState state) {
        this.state = state;
        setBackground(BookmapTheme.PANEL_BACKGROUND);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // The header is also a Swing component, so we draw it with the same
        // paintComponent pattern as the chart panels.
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
            g2.setColor(BookmapTheme.TEXT);
            g2.drawString("Toy Bookmap Heatmap", 16, 26);

            synchronized (state) {
                g2.setFont(getFont().deriveFont(Font.PLAIN, 12f));
                g2.setColor(BookmapTheme.MUTED_TEXT);
                g2.drawString(
                        "bucket: " + MarketViewState.RAW_SAMPLE_MS + " ms"
                                + "   scale: " + state.timeScaleMs() + " ms/px"
                                + "   mode: " + state.scrollMode()
                                + "   zoom: [ ]   load: +/-",
                        196,
                        26
                );
            }
        } finally {
            // Dispose the local Graphics copy created by g.create().
            g2.dispose();
        }
    }
}
