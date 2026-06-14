package io.drozda.coding.demo;

public class SyntheticStressGenerator extends EventGenerator {
    private long state = 0x9E3779B97F4A7C15L;
    private int syntheticMid = MarketConfig.MID_PRICE;
    private int drift = 1;

    @Override
    public void generateColumnEvents(int maxEvents, BookEventSink sink) {
        long now = System.nanoTime();

        for (int i = 0; i < maxEvents; i++) {
            if ((i & 4095) == 0) {
                moveMid();
            }

            int roll = nextInt(100);
            EventType type = roll < 64 ? EventType.ADD : roll < 88 ? EventType.CANCEL : EventType.TRADE;
            Side side = (nextInt(2) == 0) ? Side.BID : Side.ASK;

            int distance = 1 + nextInt(90);
            int price = side == Side.BID
                    ? syntheticMid - distance
                    : syntheticMid + distance;

            int volume = 1 + nextInt(type == EventType.ADD ? 80 : 45);
            sink.accept(now, side, clampPrice(price), volume, type);
        }
    }

    private void moveMid() {
        syntheticMid += drift + nextInt(3) - 1;

        int min = MarketConfig.MIN_PRICE + 100;
        int max = MarketConfig.MIN_PRICE + MarketConfig.PRICE_LEVELS - 100;

        if (syntheticMid <= min || syntheticMid >= max || nextInt(100) == 0) {
            drift *= -1;
            syntheticMid = Math.max(min, Math.min(max, syntheticMid));
        }
    }

    private int nextInt(int bound) {
        state ^= (state << 13);
        state ^= (state >>> 7);
        state ^= (state << 17);
        return (int) Long.remainderUnsigned(state, bound);
    }

    private int clampPrice(int price) {
        int min = MarketConfig.MIN_PRICE;
        int max = MarketConfig.MIN_PRICE + MarketConfig.PRICE_LEVELS - 1;
        return Math.max(min, Math.min(max, price));
    }
}
