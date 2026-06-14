package io.drozda.coding.demo;

final class PriceScale {
    private PriceScale() {
    }

    // One shared mapping from market price levels to screen Y coordinates.
    // HeatmapViewPanel and DepthViewPanel both call this, so their rows line up.
    static int levelToY(int priceLevel, int height) {
        double cellH = height / (double) MarketConfig.PRICE_LEVELS;
        return (int) ((MarketConfig.PRICE_LEVELS - 1 - priceLevel) * cellH);
    }

    static int priceToY(int price, int height) {
        return levelToY(MarketConfig.priceToLevel(price), height);
    }

    static boolean isValidLevel(int priceLevel) {
        return priceLevel >= 0 && priceLevel < MarketConfig.PRICE_LEVELS;
    }

    static int clampLevel(int priceLevel) {
        return Math.max(0, Math.min(MarketConfig.PRICE_LEVELS - 1, priceLevel));
    }
}
