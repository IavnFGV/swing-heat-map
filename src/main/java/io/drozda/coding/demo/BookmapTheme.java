package io.drozda.coding.demo;

import java.awt.Color;

final class BookmapTheme {
    static final Color BACKGROUND = new Color(15, 17, 22);
    static final Color PANEL_BACKGROUND = new Color(26, 31, 38);
    static final Color GRID_MAJOR = new Color(116, 130, 150, 95);
    static final Color GRID_MINOR = new Color(98, 108, 124, 45);
    static final Color TEXT = new Color(213, 220, 229);
    static final Color MUTED_TEXT = new Color(139, 151, 166);
    static final Color BID = new Color(57, 213, 190);
    static final Color ASK = new Color(236, 96, 171);
    static final Color TRADE = new Color(115, 178, 255);
    static final Color HEAT_LOW = new Color(35, 53, 113);
    static final Color HEAT_MID = new Color(103, 73, 164);
    static final Color HEAT_HIGH = new Color(244, 166, 71);
    static final Color AXIS_BACKGROUND = new Color(45, 52, 63);

    private BookmapTheme() {
    }

    static Color heatColor(int volume) {
        int intensity = (int) Math.min(255, Math.log1p(volume) * 22);
        double t = intensity / 255.0;
        if (t < 0.55) {
            return blend(HEAT_LOW, HEAT_MID, t / 0.55);
        }
        return blend(HEAT_MID, HEAT_HIGH, (t - 0.55) / 0.45);
    }

    private static Color blend(Color from, Color to, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        int r = (int) (from.getRed() + (to.getRed() - from.getRed()) * clamped);
        int g = (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * clamped);
        int b = (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * clamped);
        return new Color(r, g, b);
    }
}
