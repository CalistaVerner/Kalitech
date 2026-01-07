package org.foxesworld.kalitech.engine.api.module;

import java.util.concurrent.atomic.AtomicLong;

public final class ApiStats {

    private final AtomicLong calls = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private final AtomicLong nanosTotal = new AtomicLong();
    private final AtomicLong nanosMax = new AtomicLong();

    public void onCall(long nanos) {
        calls.incrementAndGet();
        if (nanos > 0L) {
            nanosTotal.addAndGet(nanos);

            long prev;
            do {
                prev = nanosMax.get();
                if (nanos <= prev) break;
            } while (!nanosMax.compareAndSet(prev, nanos));
        }
    }

    public void onError() {
        errors.incrementAndGet();
    }

    public long calls() {
        return calls.get();
    }

    public long errors() {
        return errors.get();
    }

    public long nanosTotal() {
        return nanosTotal.get();
    }

    public long nanosMax() {
        return nanosMax.get();
    }

    public double avgMicros() {
        long c = calls.get();
        if (c <= 0) return 0.0;
        return (nanosTotal.get() / 1000.0) / c;
    }

    public double maxMicros() {
        return nanosMax.get() / 1000.0;
    }
}