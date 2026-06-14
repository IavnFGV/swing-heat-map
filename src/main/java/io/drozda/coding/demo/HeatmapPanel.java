package io.drozda.coding.demo;

import javax.swing.*;
import java.awt.*;
import java.util.Random;

public class HeatmapPanel extends JPanel {

    private static final int PRICE_LEVELS = 200;
    private static final int TIME_BUCKETS = 800;

    private final int[][] heatmap = new int[PRICE_LEVELS][TIME_BUCKETS];
    private final Random random = new Random();

    public HeatmapPanel() {
        Timer timer = new Timer(100, e -> {
            generateFakeData();
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

        for (int i = 0; i < 100; i++) {
            int price = middle + random.nextInt(80) - 40;

            if (price >= 0 && price < PRICE_LEVELS) {
                heatmap[price][TIME_BUCKETS - 1] += random.nextInt(100);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

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
}