package io.drozda.coding.demo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Profiler {

    private static final Map<EventType, Double> metrics = new ConcurrentHashMap<>();

    public static void measure(EventType name, Runnable runnable) {
        long start = System.nanoTime();

        try {
            runnable.run();
        } finally {
            metrics.put(
                    name,
                    (System.nanoTime() - start) / 1_000_000.0
            );
        }
    }

    public static Double get(EventType name) {
        return metrics.get(name);
    }

    public enum EventType {
        APPLY_EVENTS,
        GEN_DATA,
        PAINT
    }
}
