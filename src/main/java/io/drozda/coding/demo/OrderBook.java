package io.drozda.coding.demo;

public class OrderBook {
    private static final int MIN_PRICE = 950;
    private static final int PRICE_LEVELS = 200;

    private final int[] bidVolumes = new int[PRICE_LEVELS];
    private final int[] askVolumes = new int[PRICE_LEVELS];

    public void apply(BookEvent event) {
        int priceLevel = event.price() - MIN_PRICE;

        if (priceLevel < 0 || priceLevel >= PRICE_LEVELS) {
            return;
        }

        int[] sideVolumes = event.side() == Side.BID
                ? bidVolumes
                : askVolumes;

        switch (event.type()) {
            case ADD -> sideVolumes[priceLevel] += event.volume();

            case CANCEL, TRADE -> {
                sideVolumes[priceLevel] -= event.volume();

                if (sideVolumes[priceLevel] < 0) {
                    sideVolumes[priceLevel] = 0;
                }
            }
        }
    }

    public int totalVolumeAt(int priceLevel) {
        return bidVolumes[priceLevel] + askVolumes[priceLevel];
    }
}
