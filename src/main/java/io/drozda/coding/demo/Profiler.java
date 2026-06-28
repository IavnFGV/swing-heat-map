package io.drozda.coding.demo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Profiler {

    private static final Map<EventType, Double> metrics = new ConcurrentHashMap<>();

    private Profiler() {
    }

    public static void measure(EventType eventType, Runnable runnable) {
        long start = System.nanoTime();

        try {
            runnable.run();
        } finally {
            metrics.put(
                    eventType,
                    (System.nanoTime() - start) / 1_000_000.0
            );
        }
    }

    public static double get(EventType eventType) {
        return metrics.getOrDefault(eventType, 0.0);
    }

    public enum EventType {
        GENERATE_DATA,
        PAINT
    }
}
