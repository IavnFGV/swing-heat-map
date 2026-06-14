package io.drozda.coding.demo;

import java.util.Random;
import java.util.ArrayList;
import java.util.List;

public class EventGenerator {

    private final Random random = new Random();
    private final int[] liquidityWalls = {
            MarketConfig.MID_PRICE - 34,
            MarketConfig.MID_PRICE - 18,
            MarketConfig.MID_PRICE + 22,
            MarketConfig.MID_PRICE + 46
    };

    private int syntheticMid = MarketConfig.MID_PRICE;
    private int driftDirection = 1;
    private int eventsSinceRegimeChange = 0;
    private int regimeLength = 25_000;
    private MarketRegime regime = MarketRegime.CALM;

    public BookEvent nextEvent() {
        maybeChangeRegime();
        maybeMoveSyntheticMid();
        maybeMoveLiquidityWall();

        EventType type = nextEventType();
        Side side = nextSide(type);
        int price = nextPrice(type, side);
        int volume = nextVolume(type, price);

        return new BookEvent(
                System.nanoTime(),
                side,
                price,
                volume,
                type
        );
    }

    public List<BookEvent> nextColumnEvents(int maxEvents) {
        List<BookEvent> events = new ArrayList<>(maxEvents);

        for (int i = 0; i < maxEvents; i++) {
            events.add(nextEvent());
        }

        return events;
    }

    public void generateColumnEvents(int maxEvents, BookEventSink sink) {
        for (BookEvent event : nextColumnEvents(maxEvents)) {
            sink.accept(
                    event.timestampNanos(),
                    event.side(),
                    event.price(),
                    event.volume(),
                    event.type()
            );
        }
    }

    private void maybeChangeRegime() {
        eventsSinceRegimeChange++;

        if (eventsSinceRegimeChange < regimeLength) {
            return;
        }

        eventsSinceRegimeChange = 0;
        regimeLength = 12_000 + random.nextInt(35_000);
        regime = switch (random.nextInt(4)) {
            case 0 -> MarketRegime.CALM;
            case 1 -> MarketRegime.ACTIVE;
            case 2 -> MarketRegime.IMPULSE;
            default -> MarketRegime.LIQUIDITY_WALL;
        };

        if (random.nextBoolean()) {
            driftDirection *= -1;
        }
    }

    private void maybeMoveSyntheticMid() {
        int moveChance = switch (regime) {
            case CALM -> 700;
            case ACTIVE -> 260;
            case IMPULSE -> 80;
            case LIQUIDITY_WALL -> 420;
        };

        if (random.nextInt(moveChance) != 0) {
            return;
        }

        int noise = random.nextInt(3) - 1;
        syntheticMid += driftDirection + noise;

        int minMid = MarketConfig.MIN_PRICE + 80;
        int maxMid = MarketConfig.MIN_PRICE + MarketConfig.PRICE_LEVELS - 80;

        if (syntheticMid <= minMid || syntheticMid >= maxMid) {
            driftDirection *= -1;
            syntheticMid = Math.max(minMid, Math.min(maxMid, syntheticMid));
        }
    }

    private void maybeMoveLiquidityWall() {
        if (regime != MarketRegime.LIQUIDITY_WALL || random.nextInt(1_500) != 0) {
            return;
        }

        int index = random.nextInt(liquidityWalls.length);
        int sideOffset = random.nextBoolean() ? -1 : 1;
        liquidityWalls[index] = syntheticMid + sideOffset * (12 + random.nextInt(70));
    }

    private EventType nextEventType() {
        int roll = random.nextInt(100);

        return switch (regime) {
            case CALM -> roll < 68 ? EventType.ADD : roll < 90 ? EventType.CANCEL : EventType.TRADE;
            case ACTIVE -> roll < 50 ? EventType.ADD : roll < 75 ? EventType.CANCEL : EventType.TRADE;
            case IMPULSE -> roll < 38 ? EventType.ADD : roll < 64 ? EventType.CANCEL : EventType.TRADE;
            case LIQUIDITY_WALL -> roll < 76 ? EventType.ADD : roll < 92 ? EventType.CANCEL : EventType.TRADE;
        };
    }

    private Side nextSide(EventType type) {
        if (type == EventType.TRADE) {
            return driftDirection >= 0 ? Side.ASK : Side.BID;
        }

        return random.nextBoolean() ? Side.BID : Side.ASK;
    }

    private int nextPrice(EventType type, Side side) {
        if (type == EventType.TRADE) {
            // Trades happen near the top of book: buy market orders hit asks,
            // sell market orders hit bids. This makes activity cluster near price.
            int touchOffset = 1 + random.nextInt(regime == MarketRegime.IMPULSE ? 8 : 4);
            return side == Side.ASK
                    ? syntheticMid + touchOffset
                    : syntheticMid - touchOffset;
        }

        if (regime == MarketRegime.LIQUIDITY_WALL && type == EventType.ADD && random.nextInt(100) < 62) {
            return liquidityWalls[random.nextInt(liquidityWalls.length)] + random.nextInt(3) - 1;
        }

        if (type == EventType.CANCEL && random.nextInt(100) < 35) {
            return liquidityWalls[random.nextInt(liquidityWalls.length)] + random.nextInt(5) - 2;
        }

        int distanceFromMid = randomDistanceFromMid();
        return side == Side.BID
                ? syntheticMid - distanceFromMid
                : syntheticMid + distanceFromMid;
    }

    private int randomDistanceFromMid() {
        int near = random.nextInt(18) + random.nextInt(18);

        if (random.nextInt(100) < 12) {
            return 30 + random.nextInt(110);
        }

        return Math.max(1, near);
    }

    private int nextVolume(EventType type, int price) {
        int base = switch (type) {
            case ADD -> 8 + random.nextInt(80);
            case CANCEL -> 4 + random.nextInt(60);
            case TRADE -> 1 + random.nextInt(regime == MarketRegime.IMPULSE ? 180 : 90);
        };

        for (int wall : liquidityWalls) {
            if (Math.abs(price - wall) <= 1) {
                return base + 160 + random.nextInt(420);
            }
        }

        return base;
    }

    private enum MarketRegime {
        CALM,
        ACTIVE,
        IMPULSE,
        LIQUIDITY_WALL
    }
}
