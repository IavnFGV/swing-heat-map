package io.drozda.coding.demo;

import java.awt.image.BufferedImage;

final class RenderFrame {
    final int heatmapWidth;
    final int heatmapHeight;
    final BufferedImage heatmapImage;
    final int[] bestBidPrices;
    final int[] bestAskPrices;
    final long[] gridTimestamps;
    final int referencePrice;

    final int activityWidth;
    final int[] activityVolume;
    final int[] activityDelta;
    final long[] activityCvd;

    RenderFrame(
            int heatmapWidth,
            int heatmapHeight,
            BufferedImage heatmapImage,
            int[] bestBidPrices,
            int[] bestAskPrices,
            long[] gridTimestamps,
            int referencePrice,
            int activityWidth,
            int[] activityVolume,
            int[] activityDelta,
            long[] activityCvd
    ) {
        this.heatmapWidth = heatmapWidth;
        this.heatmapHeight = heatmapHeight;
        this.heatmapImage = heatmapImage;
        this.bestBidPrices = bestBidPrices;
        this.bestAskPrices = bestAskPrices;
        this.gridTimestamps = gridTimestamps;
        this.referencePrice = referencePrice;
        this.activityWidth = activityWidth;
        this.activityVolume = activityVolume;
        this.activityDelta = activityDelta;
        this.activityCvd = activityCvd;
    }
}
