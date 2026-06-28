package io.drozda.coding.demo;

import javax.swing.*;

public class Starter {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            InfoFrame infoFrame = new InfoFrame();
            JFrame frame = new JFrame("Toy Bookmap Heatmap");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1200, 800);
            frame.setLocationRelativeTo(null);
            frame.setContentPane(new HeatmapPanel(infoFrame));
            frame.setVisible(true);
            infoFrame.setVisible(true);
        });
    }
}
