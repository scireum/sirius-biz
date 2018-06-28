/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.offheap.datastructures;

import sirius.biz.analytics.offheap.memory.Allocator;
import sirius.biz.analytics.offheap.memory.BitBuddy;
import sirius.biz.analytics.offheap.memory.LargeMemory;
import sirius.biz.analytics.offheap.memory.LargeResource;

import java.util.Optional;

public class LargeHashtable implements LargeResource {

    private static final double LOAD_FACTOR = 0.75d;
    private static final int NUMBER_OF_COLUMNS = 4;
    private LargeMemory memory;
    private final Allocator allocator;

    private long availableKeys = 0;
    private long numberOfKeys = 0;
    private long numberOfValues = 0;

    public LargeHashtable(Allocator allocator) {
        this.allocator = allocator;
        memory = new LargeMemory(allocator);
        memory.alloc(LargeMemory.PAGE_SIZE);
        availableKeys += LargeMemory.PAGE_SIZE / BitBuddy.LONG_BYTES / NUMBER_OF_COLUMNS;
    }

    private void rehash() {
        LargeMemory oldMemory = memory;
        memory = new LargeMemory(allocator);
        try {
            memory.alloc(oldMemory.getUsedSize() + LargeMemory.PAGE_SIZE);
            availableKeys += LargeMemory.PAGE_SIZE / BitBuddy.LONG_BYTES / NUMBER_OF_COLUMNS;

            for (long i = 0; i < oldMemory.getUsedSize(); i += NUMBER_OF_COLUMNS * BitBuddy.LONG_BYTES) {
                long wrappedKey = oldMemory.readLong(i);
                if (wrappedKey != 0) {
                    put(BitBuddy.unwrapLong(wrappedKey), oldMemory.readLong(i + BitBuddy.LONG_BYTES));
                }
            }
        } finally {
            oldMemory.release();
        }
    }

    public Optional<Long> get(long key) {
        int numberOfWraps = 0;
        long index = Long.hashCode(key) % availableKeys;
        long effectiveKey = BitBuddy.wrapLong(key);

        while (numberOfWraps <= 1) {
            for (int i = 0; i < NUMBER_OF_COLUMNS; i += 2) {
                long possibleKey = memory.readLong((index * NUMBER_OF_COLUMNS + i) * BitBuddy.LONG_BYTES);
                if (possibleKey == 0) {
                    return Optional.empty();
                } else if (possibleKey == effectiveKey) {
                    return Optional.of(memory.readLong((index * NUMBER_OF_COLUMNS + i + 1) * BitBuddy.LONG_BYTES));
                }
            }
            index += NUMBER_OF_COLUMNS;
            if (index >= memory.getUsedSize()) {
                index -= memory.getUsedSize();
                numberOfWraps++;
            }
        }

        return Optional.empty();
    }

    public void put(long key, long value) {
        int numberOfWraps = 0;
        long index = Long.hashCode(key) % availableKeys;
        long effectiveKey = BitBuddy.wrapLong(key);

        while (numberOfWraps <= 1) {
            for (int i = 0; i < NUMBER_OF_COLUMNS; i += 2) {
                long possibleKey = memory.readLong((index * NUMBER_OF_COLUMNS + i) * BitBuddy.LONG_BYTES);
                if (possibleKey == 0) {
                    memory.writeLong((index * NUMBER_OF_COLUMNS + i) * BitBuddy.LONG_BYTES, effectiveKey);
                    memory.writeLong((index * NUMBER_OF_COLUMNS + i + 1) * BitBuddy.LONG_BYTES, value);
                    if (i == 0) {
                        numberOfKeys++;
                        if (Math.round(availableKeys * LOAD_FACTOR) < numberOfKeys) {
                            rehash();
                        }
                    }
                    numberOfValues++;
                } else if (possibleKey == effectiveKey) {
                    memory.writeLong((index * NUMBER_OF_COLUMNS + i + 1) * BitBuddy.LONG_BYTES, value);
                }
            }
            index += NUMBER_OF_COLUMNS;
            if (index >= memory.getUsedSize()) {
                index -= memory.getUsedSize();
                numberOfWraps++;
            }
        }
    }

    @Override
    public void release() {
        memory.release();
    }
}
