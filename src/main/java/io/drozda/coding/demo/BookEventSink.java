package io.drozda.coding.demo;

@FunctionalInterface
interface BookEventSink {
    void accept(long timestampNanos, Side side, int price, int volume, EventType type);
}
