package io.drozda.coding.demo;

final class PriceDisplay {
    private static volatile boolean realPriceMode = false;
    private static volatile double baseRealPrice = 0.0;
    private static volatile double tickSize = 1.0;

    private PriceDisplay() {
    }

    static void configureRealPrices(double basePrice, double tick) {
        baseRealPrice = basePrice;
        tickSize = tick;
        realPriceMode = true;
    }

    static void clearRealPrices() {
        realPriceMode = false;
        baseRealPrice = 0.0;
        tickSize = 1.0;
    }

    static String formatInternalPrice(int internalPrice) {
        if (!realPriceMode) {
            return String.valueOf(internalPrice);
        }

        double realPrice = baseRealPrice + (internalPrice - MarketConfig.MID_PRICE) * tickSize;
        return String.format("%.2f", realPrice);
    }
}
