package io.drozda.coding.demo;

public class OrderBook {
    private final int[] bidVolumes = new int[MarketConfig.PRICE_LEVELS];
    private final int[] askVolumes = new int[MarketConfig.PRICE_LEVELS];

    public void apply(BookEvent event) {
        int priceLevel = MarketConfig.priceToLevel(event.price());

        if (priceLevel < 0 || priceLevel >= MarketConfig.PRICE_LEVELS) {
            return;
        }

        int[] sideVolumes = event.side() == Side.BID ? bidVolumes : askVolumes;

        switch (event.type()) {
            case ADD -> sideVolumes[priceLevel] += event.volume();
            case CANCEL, TRADE -> sideVolumes[priceLevel] =
                    Math.max(0, sideVolumes[priceLevel] - event.volume());
        }
    }

    public int totalVolumeAt(int priceLevel) {
        return bidVolumes[priceLevel] + askVolumes[priceLevel];
    }

    public int bestBidPrice() {
        for (int i = bidVolumes.length - 1; i >= 0; i--) {
            if (bidVolumes[i] > 0) {
                return MarketConfig.levelToPrice(i);
            }
        }
        return -1;
    }

    public int bestAskPrice() {
        for (int i = 0; i < askVolumes.length; i++) {
            if (askVolumes[i] > 0) {
                return MarketConfig.levelToPrice(i);
            }
        }
        return -1;
    }
}
