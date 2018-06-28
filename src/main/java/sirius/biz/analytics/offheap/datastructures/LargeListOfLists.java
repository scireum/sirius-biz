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

import java.util.function.Function;

public class LargeListOfLists implements LargeResource {

    private LargeMemory memory;

    public LargeListOfLists(Allocator allocator) {
        this.memory = new LargeMemory(allocator);
    }

    public long createList() {
        return memory.alloc(2 * BitBuddy.LONG_BYTES);
    }

    public void append(long list, long value) {
        long cell = memory.alloc(2 * BitBuddy.LONG_BYTES);
        memory.writeLong(cell, value);

        long lastCell = memory.readLong(list);
        if (lastCell > 0) {
            memory.writeLong(lastCell + BitBuddy.LONG_BYTES, cell);
        } else {
            memory.writeLong(list + BitBuddy.LONG_BYTES, cell);
        }
        memory.writeLong(list, cell);
    }

    public void iterate(long list, Function<Long, Boolean> listConsumer) {
        long cell = memory.readLong(list + BitBuddy.LONG_BYTES);
        while (cell != 0) {
            if (!listConsumer.apply(memory.readLong(cell))) {
                return;
            }
            cell = memory.readLong(cell + BitBuddy.LONG_BYTES);
        }
    }

    @Override
    public void release() {
        memory.release();
    }
}
