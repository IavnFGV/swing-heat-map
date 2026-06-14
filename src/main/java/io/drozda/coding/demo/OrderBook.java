package io.drozda.coding.demo;

import java.util.Arrays;

public class OrderBook {
    private final int[] bidVolumes = new int[MarketConfig.PRICE_LEVELS];
    private final int[] askVolumes = new int[MarketConfig.PRICE_LEVELS];

    public void apply(BookEvent event) {
        apply(event.side(), event.price(), event.volume(), event.type());
    }

    public void apply(Side side, int price, int volume, EventType type) {
        int priceLevel = MarketConfig.priceToLevel(price);

        if (priceLevel < 0 || priceLevel >= MarketConfig.PRICE_LEVELS) {
            return;
        }

        int[] sideVolumes = side == Side.BID ? bidVolumes : askVolumes;

        switch (type) {
            case ADD -> sideVolumes[priceLevel] += volume;
            case CANCEL, TRADE -> sideVolumes[priceLevel] =
                    Math.max(0, sideVolumes[priceLevel] - volume);
        }
    }

    public int totalVolumeAt(int priceLevel) {
        return bidVolumes[priceLevel] + askVolumes[priceLevel];
    }

    public int bidVolumeAt(int priceLevel) {
        return bidVolumes[priceLevel];
    }

    public int askVolumeAt(int priceLevel) {
        return askVolumes[priceLevel];
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

    public void clear() {
        Arrays.fill(bidVolumes, 0);
        Arrays.fill(askVolumes, 0);
    }
}
