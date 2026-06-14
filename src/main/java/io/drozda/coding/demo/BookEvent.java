package io.drozda.coding.demo;

public record BookEvent(
        long timestampNanos,
        Side side,
        int price,
        int volume,
        EventType type
) {
}