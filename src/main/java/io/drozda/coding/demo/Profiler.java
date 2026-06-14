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

    public static void record(EventType name, double millis) {
        metrics.put(name, millis);
    }

    public static String report() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<EventType, Double> entry : metrics.entrySet()) {
            sb.append('\n');
            sb.append(entry.getKey());
            sb.append(": ");
            sb.append(entry.getValue());
        }
        sb.append("\n");

        return sb.toString();
    }

    public enum EventType {
        APPLY_EVENTS,
        GEN_DATA,
        STATE_LOCK_WAIT,
        NEXT_EVENTS,
        ORDERBOOK_APPLY,
        PAINT,
        SCROLL_COPY
    }
}
