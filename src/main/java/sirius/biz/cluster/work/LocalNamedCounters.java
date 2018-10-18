/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides local (single machine) implementation for a set of counters.
 */
class LocalNamedCounters implements NamedCounters {

    private final Map<String, AtomicInteger> counters = new HashMap<>();

    @Override
    public long incrementAndGet(String counter) {
        synchronized (counters) {
            return counters.computeIfAbsent(counter, ignored -> new AtomicInteger(0)).incrementAndGet();
        }
    }

    @Override
    public long get(String counter) {
        synchronized (counters) {
            AtomicInteger value = counters.get(counter);
            return value == null ? 0 : value.get();
        }
    }

    @Override
    public long decrementAndGet(String counter) {
        synchronized (counters) {
            AtomicInteger value = counters.get(counter);
            if (value == null) {
                return 0;
            }

            int decrementedValue = value.decrementAndGet();
            if (decrementedValue <= 0) {
                counters.remove(counter);
                return 0;
            }

            return decrementedValue;
        }
    }
}
