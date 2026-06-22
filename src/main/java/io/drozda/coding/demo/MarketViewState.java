package io.drozda.coding.demo;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

final class MarketViewState {
    static final int RAW_SAMPLE_MS = 20;
    static final int[] TIME_SCALES_MS = {20, 100, 1000};

    // This object is the shared "view model" for all panels. The market worker
    // updates it, and Swing panels render it. That keeps UI layout separate from
    // market-data calculation.
    final int[][] heatmap = new int[MarketConfig.PRICE_LEVELS][MarketConfig.TIME_BUCKETS];
    final int[] bestBidHistory = new int[MarketConfig.TIME_BUCKETS];
    final int[] bestAskHistory = new int[MarketConfig.TIME_BUCKETS];
    final int[] tradeVolumeHistory = new int[MarketConfig.TIME_BUCKETS];
    final int[] tradeDeltaHistory = new int[MarketConfig.TIME_BUCKETS];
    final long[] cvdHistory = new long[MarketConfig.TIME_BUCKETS];
    final long[] timestampMicrosHistory = new long[MarketConfig.TIME_BUCKETS];

    private final OrderBook orderBook = new OrderBook();

    private ScrollMode scrollMode = ScrollMode.CIRCULAR_BUFFER;
    private DataMode dataMode = DataMode.HISTORICAL_REPLAY;
    private int currentColumn = MarketConfig.TIME_BUCKETS - 1;
    private int timeScaleIndex = 1;
    private int eventsPerTick = MarketConfig.EVENTS_PER_TICK;
    private int lastTickTradedVolume = 0;
    private int lastTickDelta = 0;
    private long cumulativeDelta = 0L;
    private long lastTimestampMicros = 0L;
    private long latestColumnTimestampMicros = 0L;
    private int referencePrice = MarketConfig.MID_PRICE;

    private long totalEvents = 0;
    private long totalTradedVolume = 0;
    private volatile int renderHeatmapWidth = 0;
    private volatile int renderHeatmapHeight = 0;
    private volatile int renderActivityWidth = 0;
    private volatile RenderFrame latestFrame;

    MarketViewState() {
        reset(DataMode.HISTORICAL_REPLAY);
    }

    synchronized void reset(DataMode dataMode) {
        for (int y = 0; y < MarketConfig.PRICE_LEVELS; y++) {
            Arrays.fill(heatmap[y], 0);
        }

        Arrays.fill(bestBidHistory, -1);
        Arrays.fill(bestAskHistory, -1);
        Arrays.fill(tradeVolumeHistory, 0);
        Arrays.fill(tradeDeltaHistory, 0);
        Arrays.fill(cvdHistory, 0L);
        Arrays.fill(timestampMicrosHistory, 0L);

        orderBook.clear();

        this.dataMode = dataMode;
        currentColumn = MarketConfig.TIME_BUCKETS - 1;
        lastTickTradedVolume = 0;
        lastTickDelta = 0;
        cumulativeDelta = 0L;
        lastTimestampMicros = 0L;
        latestColumnTimestampMicros = 0L;
        referencePrice = MarketConfig.MID_PRICE;
        totalEvents = 0;
        totalTradedVolume = 0;
    }

    void generate(EventGenerator eventGenerator) {
        long lockWaitStart = System.nanoTime();

        synchronized (this) {
            Profiler.record(
                    Profiler.EventType.STATE_LOCK_WAIT,
                    (System.nanoTime() - lockWaitStart) / 1_000_000.0
            );

            generateLocked(eventGenerator);
        }
    }

    private void generateLocked(EventGenerator eventGenerator) {
        // For now generation and rendering share this object under one monitor.
        // Later this can become a published immutable frame or double buffer.
        if (scrollMode == ScrollMode.SHIFT_COPY) {
            generateWithShiftCopy(eventGenerator);
        } else {
            generateWithCircularBuffer(eventGenerator);
        }

        Profiler.measure(Profiler.EventType.RENDER_FRAME_BUILD, this::buildRenderFrame);
    }

    void setRenderViewport(int heatmapWidth, int heatmapHeight, int activityWidth) {
        renderHeatmapWidth = Math.max(0, heatmapWidth);
        renderHeatmapHeight = Math.max(0, heatmapHeight);
        renderActivityWidth = Math.max(0, activityWidth);
    }

    RenderFrame latestFrame() {
        return latestFrame;
    }

    private void generateWithShiftCopy(EventGenerator eventGenerator) {
        Profiler.measure(Profiler.EventType.SCROLL_COPY, () -> {
            for (int y = 0; y < MarketConfig.PRICE_LEVELS; y++) {
                System.arraycopy(heatmap[y], 1, heatmap[y], 0, MarketConfig.TIME_BUCKETS - 1);
                heatmap[y][MarketConfig.TIME_BUCKETS - 1] = 0;
            }

            System.arraycopy(bestBidHistory, 1, bestBidHistory, 0, MarketConfig.TIME_BUCKETS - 1);
            System.arraycopy(bestAskHistory, 1, bestAskHistory, 0, MarketConfig.TIME_BUCKETS - 1);
            System.arraycopy(tradeVolumeHistory, 1, tradeVolumeHistory, 0, MarketConfig.TIME_BUCKETS - 1);
            System.arraycopy(tradeDeltaHistory, 1, tradeDeltaHistory, 0, MarketConfig.TIME_BUCKETS - 1);
            System.arraycopy(cvdHistory, 1, cvdHistory, 0, MarketConfig.TIME_BUCKETS - 1);
            System.arraycopy(timestampMicrosHistory, 1, timestampMicrosHistory, 0, MarketConfig.TIME_BUCKETS - 1);
        });

        Profiler.measure(Profiler.EventType.APPLY_EVENTS, () -> applyEvents(eventGenerator));
        Profiler.measure(Profiler.EventType.GEN_DATA, () -> writeColumn(MarketConfig.TIME_BUCKETS - 1));

        currentColumn = MarketConfig.TIME_BUCKETS - 1;
    }

    private void generateWithCircularBuffer(EventGenerator eventGenerator) {
        Profiler.measure(Profiler.EventType.SCROLL_COPY, () -> {
            currentColumn = (currentColumn + 1) % MarketConfig.TIME_BUCKETS;

            for (int priceLevel = 0; priceLevel < MarketConfig.PRICE_LEVELS; priceLevel++) {
                heatmap[priceLevel][currentColumn] = 0;
            }

            bestBidHistory[currentColumn] = -1;
            bestAskHistory[currentColumn] = -1;
            tradeVolumeHistory[currentColumn] = 0;
            tradeDeltaHistory[currentColumn] = 0;
            cvdHistory[currentColumn] = cumulativeDelta;
            timestampMicrosHistory[currentColumn] = 0L;
        });

        Profiler.measure(Profiler.EventType.APPLY_EVENTS, () -> applyEvents(eventGenerator));
        Profiler.measure(Profiler.EventType.GEN_DATA, () -> writeColumn(currentColumn));
    }

    private void applyEvents(EventGenerator eventGenerator) {
        final int[] tradedVolume = {0};
        final int[] delta = {0};
        final long[] count = {0};

        Profiler.measure(Profiler.EventType.ORDERBOOK_APPLY, () -> {
            eventGenerator.generateColumnEvents(eventsPerTick, (timestampNanos, side, price, volume, type) -> {
                orderBook.apply(side, price, volume, type);

                lastTimestampMicros = timestampNanos / 1_000L;
                count[0]++;

                if (type == EventType.TRADE) {
                    referencePrice = price;
                    totalTradedVolume += volume;
                    tradedVolume[0] += volume;
                    delta[0] += side == Side.ASK ? volume : -volume;
                }
            });
        });

        totalEvents += count[0];
        lastTickTradedVolume = tradedVolume[0];
        lastTickDelta = delta[0];
        cumulativeDelta += delta[0];
    }

    private void writeColumn(int column) {
        for (int priceLevel = 0; priceLevel < MarketConfig.PRICE_LEVELS; priceLevel++) {
            heatmap[priceLevel][column] = totalVolumeAt(priceLevel);
        }

        bestBidHistory[column] = bestBidPrice();
        bestAskHistory[column] = bestAskPrice();
        tradeVolumeHistory[column] = lastTickTradedVolume;
        tradeDeltaHistory[column] = lastTickDelta;
        cvdHistory[column] = cumulativeDelta;
        timestampMicrosHistory[column] = lastTimestampMicros;

        if (lastTimestampMicros > 0L) {
            latestColumnTimestampMicros = lastTimestampMicros;
        }
    }

    private void buildRenderFrame() {
        int heatmapWidth = renderHeatmapWidth;
        int heatmapHeight = renderHeatmapHeight;
        int activityWidth = renderActivityWidth;

        if (heatmapWidth <= 0 || heatmapHeight <= 0 || activityWidth <= 0) {
            return;
        }

        BufferedImage heatmapImage = new BufferedImage(heatmapWidth, heatmapHeight, BufferedImage.TYPE_INT_RGB);
        int[] pixels = ((DataBufferInt) heatmapImage.getRaster().getDataBuffer()).getData();
        int background = BookmapTheme.BACKGROUND.getRGB();

        for (int screenY = 0; screenY < heatmapHeight; screenY++) {
            int priceLevel = PriceScale.clampLevel(
                    MarketConfig.PRICE_LEVELS - 1
                            - (int) (screenY * (MarketConfig.PRICE_LEVELS / (double) heatmapHeight))
            );

            for (int screenX = 0; screenX < heatmapWidth; screenX++) {
                int bufferColumn = visibleScreenXToBufferColumn(screenX, heatmapWidth);
                int volume = bufferColumn == -1 ? 0 : heatmap[priceLevel][bufferColumn];
                pixels[screenY * heatmapWidth + screenX] = volume == 0
                        ? background
                        : BookmapTheme.heatRgb(volume);
            }
        }

        int[] bestBidPrices = new int[heatmapWidth];
        int[] bestAskPrices = new int[heatmapWidth];

        for (int x = 0; x < heatmapWidth; x++) {
            int column = visibleScreenXToBufferColumn(x, heatmapWidth);
            if (column == -1) {
                bestBidPrices[x] = -1;
                bestAskPrices[x] = -1;
            } else {
                bestBidPrices[x] = bestBidHistory[column];
                bestAskPrices[x] = bestAskHistory[column];
            }
        }

        long[] gridTimestamps = new long[11];
        for (int i = 0; i < gridTimestamps.length; i++) {
            int x = i * heatmapWidth / (gridTimestamps.length - 1);
            gridTimestamps[i] = visibleTimestampMicros(x, heatmapWidth);
        }

        int[] activityVolume = new int[activityWidth];
        int[] activityDelta = new int[activityWidth];
        long[] activityCvd = new long[activityWidth];

        for (int x = 0; x < activityWidth; x++) {
            activityVolume[x] = visibleTradeVolumeBucket(x, activityWidth);
            activityDelta[x] = visibleTradeDeltaBucket(x, activityWidth);
            activityCvd[x] = visibleCvd(x, activityWidth);
        }

        latestFrame = new RenderFrame(
                heatmapWidth,
                heatmapHeight,
                heatmapImage,
                bestBidPrices,
                bestAskPrices,
                gridTimestamps,
                referencePrice,
                activityWidth,
                activityVolume,
                activityDelta,
                activityCvd
        );
    }

    int screenXToBufferColumn(int screenX) {
        // SHIFT_COPY keeps logical and physical columns equal.
        // CIRCULAR_BUFFER stores newest data by rotating currentColumn, so each
        // screen x must be translated to the real buffer column.
        if (scrollMode == ScrollMode.SHIFT_COPY) {
            return screenX;
        }

        return (currentColumn + 1 + screenX) % MarketConfig.TIME_BUCKETS;
    }

    int visibleScreenXToBufferColumn(int screenX, int screenWidth) {
        int rawColumnsPerPixel = rawColumnsPerScreenPixel();
        int rawOffsetFromNewest = (screenWidth - 1 - screenX) * rawColumnsPerPixel;

        return bufferColumnFromNewestOffset(rawOffsetFromNewest);
    }

    int visibleTradeVolumeBucket(int screenX, int screenWidth) {
        int rawColumnsPerPixel = rawColumnsPerScreenPixel();
        int firstOffsetFromNewest = (screenWidth - 1 - screenX) * rawColumnsPerPixel;
        int volume = 0;

        for (int i = 0; i < rawColumnsPerPixel; i++) {
            int column = bufferColumnFromNewestOffset(firstOffsetFromNewest + i);
            if (column == -1) {
                continue;
            }

            volume += tradeVolumeHistory[column];
        }

        return volume;
    }

    int visibleTradeDeltaBucket(int screenX, int screenWidth) {
        int rawColumnsPerPixel = rawColumnsPerScreenPixel();
        int firstOffsetFromNewest = (screenWidth - 1 - screenX) * rawColumnsPerPixel;
        int delta = 0;

        for (int i = 0; i < rawColumnsPerPixel; i++) {
            int column = bufferColumnFromNewestOffset(firstOffsetFromNewest + i);
            if (column == -1) {
                continue;
            }

            delta += tradeDeltaHistory[column];
        }

        return delta;
    }

    long visibleCvd(int screenX, int screenWidth) {
        int column = visibleScreenXToBufferColumn(screenX, screenWidth);
        if (column == -1) {
            return 0L;
        }

        return cvdHistory[column];
    }

    long visibleTimestampMicros(int screenX, int screenWidth) {
        if (latestColumnTimestampMicros == 0L) {
            return 0L;
        }

        long microsBack = (long) (screenWidth - 1 - screenX) * timeScaleMs() * 1_000L;
        return latestColumnTimestampMicros - microsBack;
    }

    private int rawColumnsPerScreenPixel() {
        return Math.max(1, timeScaleMs() / RAW_SAMPLE_MS);
    }

    private int bufferColumnFromNewestOffset(int rawOffsetFromNewest) {
        if (rawOffsetFromNewest >= MarketConfig.TIME_BUCKETS) {
            return -1;
        }

        if (scrollMode == ScrollMode.SHIFT_COPY) {
            return MarketConfig.TIME_BUCKETS - 1 - rawOffsetFromNewest;
        }

        int column = (currentColumn - rawOffsetFromNewest) % MarketConfig.TIME_BUCKETS;
        if (column < 0) {
            column += MarketConfig.TIME_BUCKETS;
        }

        return column;
    }

    int totalVolumeAt(int priceLevel) {
        return bidVolumeAt(priceLevel) + askVolumeAt(priceLevel);
    }

    int bidVolumeAt(int priceLevel) {
        if (priceLevel >= referenceLevel()) {
            return 0;
        }

        return orderBook.bidVolumeAt(priceLevel);
    }

    int askVolumeAt(int priceLevel) {
        if (priceLevel <= referenceLevel()) {
            return 0;
        }

        return orderBook.askVolumeAt(priceLevel);
    }

    int bestBidPrice() {
        for (int i = referenceLevel() - 1; i >= 0; i--) {
            if (orderBook.bidVolumeAt(i) > 0) {
                return MarketConfig.levelToPrice(i);
            }
        }

        return -1;
    }

    int bestAskPrice() {
        for (int i = referenceLevel() + 1; i < MarketConfig.PRICE_LEVELS; i++) {
            if (orderBook.askVolumeAt(i) > 0) {
                return MarketConfig.levelToPrice(i);
            }
        }

        return -1;
    }

    int referencePrice() {
        return referencePrice;
    }

    private int referenceLevel() {
        return PriceScale.clampLevel(MarketConfig.priceToLevel(referencePrice));
    }

    int timeScaleMs() {
        return TIME_SCALES_MS[timeScaleIndex];
    }

    ScrollMode scrollMode() {
        return scrollMode;
    }

    int currentColumn() {
        return currentColumn;
    }

    int eventsPerTick() {
        return eventsPerTick;
    }

    DataMode dataMode() {
        return dataMode;
    }

    long totalEvents() {
        return totalEvents;
    }

    long totalTradedVolume() {
        return totalTradedVolume;
    }

    void setScrollMode(ScrollMode scrollMode) {
        this.scrollMode = scrollMode;
    }

    void increaseLoad() {
        eventsPerTick *= 2;
    }

    void decreaseLoad() {
        eventsPerTick = Math.max(1, eventsPerTick / 2);
    }

    void zoomOut() {
        timeScaleIndex = Math.min(TIME_SCALES_MS.length - 1, timeScaleIndex + 1);
    }

    void zoomIn() {
        timeScaleIndex = Math.max(0, timeScaleIndex - 1);
    }

}
