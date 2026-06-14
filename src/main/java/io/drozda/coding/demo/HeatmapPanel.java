package io.drozda.coding.demo;

import javax.swing.*;
import java.awt.*;

public class HeatmapPanel extends JPanel {

    private final int[][] heatmap =
            new int[MarketConfig.PRICE_LEVELS][MarketConfig.TIME_BUCKETS];

    private final EventGenerator eventGenerator = new EventGenerator();
    private final OrderBook orderBook = new OrderBook();

    public HeatmapPanel() {
        Timer timer = new Timer(16, e -> {
            generateFakeData();
            repaint();
        });
        timer.start();
    }

    private void generateFakeData() {
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

        for (int i = 0; i < MarketConfig.EVENTS_PER_TICK; i++) {
            BookEvent event = eventGenerator.nextEvent();
            orderBook.apply(event);
        }

        for (int priceLevel = 0; priceLevel < MarketConfig.PRICE_LEVELS; priceLevel++) {
            heatmap[priceLevel][MarketConfig.TIME_BUCKETS - 1] =
                    orderBook.totalVolumeAt(priceLevel);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int w = getWidth();
        int h = getHeight();

        double cellW = w / (double) MarketConfig.TIME_BUCKETS;
        double cellH = h / (double) MarketConfig.PRICE_LEVELS;

        for (int y = 0; y < MarketConfig.PRICE_LEVELS; y++) {
            for (int x = 0; x < MarketConfig.TIME_BUCKETS; x++) {
                int volume = heatmap[y][x];

                if (volume == 0) {
                    continue;
                }

                int intensity = Math.min(255, volume * 4);
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

        int midPriceLevel = MarketConfig.priceToLevel(MarketConfig.MID_PRICE);
        int midY = (int) ((MarketConfig.PRICE_LEVELS - 1 - midPriceLevel) * cellH);

        g.setColor(Color.CYAN);
        g.drawLine(0, midY, w, midY);
        g.drawString("mid: " + MarketConfig.MID_PRICE, 20, midY - 5);

        g.setColor(Color.BLACK);
        g.drawString("Synthetic liquidity heatmap", 20, 30);
        g.drawString("Events/tick: " + MarketConfig.EVENTS_PER_TICK, 20, 50);
        g.drawString("Approx events/sec: " + (MarketConfig.EVENTS_PER_TICK * 60), 20, 70);
    }
}