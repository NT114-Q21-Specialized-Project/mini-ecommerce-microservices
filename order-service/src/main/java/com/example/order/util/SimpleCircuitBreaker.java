package com.example.order.util;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleCircuitBreaker {

    private final String name;
    private final int failureThreshold;
    private final long openDurationMs;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long openUntilEpochMs = 0;

    public SimpleCircuitBreaker(String name, int failureThreshold, long openDurationMs) {
        this.name = name;
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDurationMs = Math.max(1000, openDurationMs);
    }

    public synchronized <T> T execute(Callable<T> callable) throws Exception {
        long now = System.currentTimeMillis();
        if (now < openUntilEpochMs) {
            throw new IllegalStateException(name + " circuit is OPEN");
        }

        try {
            T result = callable.call();
            consecutiveFailures.set(0);
            return result;
        } catch (Exception ex) {
            int failures = consecutiveFailures.incrementAndGet();
            if (failures >= failureThreshold) {
                openUntilEpochMs = System.currentTimeMillis() + openDurationMs;
                consecutiveFailures.set(0);
            }
            throw ex;
        }
    }
}
