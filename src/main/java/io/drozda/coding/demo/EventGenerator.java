package io.drozda.coding.demo;

import java.util.Random;

public class EventGenerator {

    private final Random random = new Random();

    public BookEvent nextEvent() {
        Side side = random.nextBoolean() ? Side.BID : Side.ASK;

        int distanceFromMid = random.nextInt(MarketConfig.PRICE_LEVELS / 4);

        int price = side == Side.BID
                ? MarketConfig.MID_PRICE - distanceFromMid
                : MarketConfig.MID_PRICE + distanceFromMid;

        EventType type = switch (random.nextInt(3)) {
            case 0 -> EventType.ADD;
            case 1 -> EventType.CANCEL;
            default -> EventType.TRADE;
        };

        int volume = 1 + random.nextInt(100);

        return new BookEvent(
                System.nanoTime(),
                side,
                price,
                volume,
                type
        );
    }
}