package io.drozda.coding.demo;

public final class MarketConfig {
    public static final int PRICE_LEVELS = 200;
    public static final int TIME_BUCKETS = 800;
    public static final int MID_PRICE = 1000;
    public static final int MIN_PRICE = MID_PRICE - PRICE_LEVELS / 2;
    public static final int EVENTS_PER_TICK = 1000;

    private MarketConfig() {
    }

    public static int priceToLevel(int price) {
        return price - MIN_PRICE;
    }

    public static int levelToPrice(int level) {
        return MIN_PRICE + level;
    }
}
