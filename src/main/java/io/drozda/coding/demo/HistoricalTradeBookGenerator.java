package io.drozda.coding.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

public class HistoricalTradeBookGenerator extends EventGenerator {
    private static final double PRICE_TICK = 1.0;
    private static final int WALL_COUNT = 5;

    private final Random random = new Random();
    private final BufferedReader reader;
    private final Queue<BookEvent> pendingEvents = new ArrayDeque<>();
    private final int[] wallOffsets = {-80, -42, -18, 34, 72};

    private double baseRealPrice = Double.NaN;
    private int anchorPrice = MarketConfig.MID_PRICE;
    private long lastTimestampMicros = System.nanoTime() / 1_000L;
    private AggTrade lookaheadTrade;
    private long currentBucketStartMicros = Long.MIN_VALUE;

    public HistoricalTradeBookGenerator(Path csvPath) {
        try {
            reader = Files.newBufferedReader(csvPath);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot open aggTrades file: " + csvPath, e);
        }
    }

    @Override
    public BookEvent nextEvent() {
        while (pendingEvents.isEmpty()) {
            AggTrade trade = readNextTrade();
            if (trade == null) {
                return super.nextEvent();
            }

            enqueueSyntheticBookReaction(trade);
        }

        return pendingEvents.remove();
    }

    @Override
    public List<BookEvent> nextColumnEvents(int maxEvents) {
        List<BookEvent> events = new ArrayList<>();

        while (!pendingEvents.isEmpty() && events.size() < maxEvents) {
            events.add(pendingEvents.remove());
        }

        if (!events.isEmpty()) {
            return events;
        }

        long bucketEndMicros = nextBucketEndMicros();

        while (events.size() < maxEvents) {
            AggTrade trade = peekTrade();
            if (trade == null) {
                break;
            }

            if (trade.timestampMicros() >= bucketEndMicros && !events.isEmpty()) {
                break;
            }

            if (trade.timestampMicros() >= bucketEndMicros) {
                currentBucketStartMicros = trade.timestampMicros();
                bucketEndMicros = currentBucketStartMicros + MarketViewState.RAW_SAMPLE_MS * 1_000L;
            }

            lookaheadTrade = null;
            enqueueSyntheticBookReaction(trade);

            while (!pendingEvents.isEmpty() && events.size() < maxEvents) {
                events.add(pendingEvents.remove());
            }
        }

        return events;
    }

    private long nextBucketEndMicros() {
        AggTrade trade = peekTrade();
        if (trade == null) {
            return Long.MAX_VALUE;
        }

        if (currentBucketStartMicros == Long.MIN_VALUE) {
            currentBucketStartMicros = trade.timestampMicros();
        } else {
            currentBucketStartMicros += MarketViewState.RAW_SAMPLE_MS * 1_000L;

            if (trade.timestampMicros() >= currentBucketStartMicros + MarketViewState.RAW_SAMPLE_MS * 1_000L) {
                currentBucketStartMicros = trade.timestampMicros();
            }
        }

        return currentBucketStartMicros + MarketViewState.RAW_SAMPLE_MS * 1_000L;
    }

    private AggTrade peekTrade() {
        if (lookaheadTrade == null) {
            lookaheadTrade = readNextTrade();
        }

        return lookaheadTrade;
    }

    private AggTrade readNextTrade() {
        try {
            String line = reader.readLine();
            if (line == null) {
                return null;
            }

            String[] parts = line.split(",");
            if (parts.length < 8) {
                return null;
            }

            return new AggTrade(
                    Long.parseLong(parts[0]),
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Long.parseLong(parts[5]),
                    Boolean.parseBoolean(parts[6])
            );
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private void enqueueSyntheticBookReaction(AggTrade trade) {
        if (Double.isNaN(baseRealPrice)) {
            baseRealPrice = trade.price();
            PriceDisplay.configureRealPrices(baseRealPrice, PRICE_TICK);
        }

        lastTimestampMicros = trade.timestampMicros();
        anchorPrice = normalizePrice(trade.price());

        // aggTrades only contain executed trades, not passive order book updates.
        // We use the real trade as the price/time/volume anchor, then generate a
        // plausible passive book around it.
        enqueuePassiveLiquidity(anchorPrice);
        if (random.nextInt(100) < 8) {
            enqueueLiquidityWalls(anchorPrice);
        }
        enqueueCancels(anchorPrice);
        enqueueAggressiveTrade(trade);
    }

    private int normalizePrice(double realPrice) {
        int internalPrice = MarketConfig.MID_PRICE + (int) Math.round((realPrice - baseRealPrice) / PRICE_TICK);
        return clampPrice(internalPrice);
    }

    private void enqueuePassiveLiquidity(int anchor) {
        int orders = 2 + random.nextInt(5);

        for (int i = 0; i < orders; i++) {
            Side side = random.nextBoolean() ? Side.BID : Side.ASK;
            int distance = 1 + random.nextInt(45);
            int price = side == Side.BID ? anchor - distance : anchor + distance;
            int volume = 1 + random.nextInt(16);

            pendingEvents.add(event(side, price, volume, EventType.ADD));
        }
    }

    private void enqueueLiquidityWalls(int anchor) {
        for (int i = 0; i < WALL_COUNT; i++) {
            int wallPrice = clampPrice(anchor + wallOffsets[i] + random.nextInt(5) - 2);
            Side side = wallPrice < anchor ? Side.BID : Side.ASK;
            int volume = 25 + random.nextInt(120);

            pendingEvents.add(event(side, wallPrice, volume, EventType.ADD));
        }
    }

    private void enqueueCancels(int anchor) {
        int cancels = 5 + random.nextInt(10);

        for (int i = 0; i < cancels; i++) {
            Side side = random.nextBoolean() ? Side.BID : Side.ASK;
            int price;

            if (random.nextInt(100) < 45) {
                price = clampPrice(anchor + wallOffsets[random.nextInt(wallOffsets.length)] + random.nextInt(7) - 3);
                side = price < anchor ? Side.BID : Side.ASK;
            } else {
                int distance = 1 + random.nextInt(80);
                price = side == Side.BID ? anchor - distance : anchor + distance;
            }

            int volume = 8 + random.nextInt(120);

            pendingEvents.add(event(side, price, volume, EventType.CANCEL));
        }
    }

    private void enqueueAggressiveTrade(AggTrade trade) {
        Side restingSide = trade.buyerAggressor() ? Side.ASK : Side.BID;
        int tradePrice = clampPrice(anchorPrice + (trade.buyerAggressor() ? 1 : -1));
        int volume = scaleTradeVolume(trade.quantity());

        // Make sure the synthetic book has resting liquidity at the trade level
        // before the trade consumes it. That keeps the sequence explainable.
        pendingEvents.add(event(restingSide, tradePrice, volume + random.nextInt(40), EventType.ADD));
        pendingEvents.add(event(restingSide, tradePrice, volume, EventType.TRADE));
    }

    private int scaleTradeVolume(double quantity) {
        return Math.max(1, Math.min(300, (int) Math.round(quantity * 10_000)));
    }

    private BookEvent event(Side side, int price, int volume, EventType type) {
        return new BookEvent(
                lastTimestampMicros * 1_000L,
                side,
                clampPrice(price),
                volume,
                type
        );
    }

    private int clampPrice(int price) {
        int min = MarketConfig.MIN_PRICE;
        int max = MarketConfig.MIN_PRICE + MarketConfig.PRICE_LEVELS - 1;
        return Math.max(min, Math.min(max, price));
    }
}
