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
import sirius.biz.analytics.offheap.memory.LargeMemoryPool;
import sirius.biz.analytics.offheap.memory.LargeResource;
import sirius.kernel.commons.Strings;

import java.util.concurrent.atomic.AtomicLong;

public class LargeTable implements LargeResource {

    private LargeMemory memory;
    private final int columns;
    private final int recordLength;
    private AtomicLong numberOfRows = new AtomicLong();

    public LargeTable(LargeMemoryPool pool, String name, int columns) {
        this(columns);
        this.memory = new LargeMemory(new Allocator(pool, "LargeTable: " + name, this));
    }

    public LargeTable(Allocator allocator, int columns) {
        this(columns);
        this.memory = new LargeMemory(allocator);
    }

    private LargeTable(int columns) {
        this.columns = columns;
        this.recordLength = columns * BitBuddy.LONG_BYTES;
    }

    public long appendRow() {
        memory.alloc(recordLength);
        return numberOfRows.getAndIncrement();
    }

    public void writeCell(long row, int col, long value) {
        memory.writeLong(row * recordLength + col * BitBuddy.LONG_BYTES, value);
    }

    public long readCell(long row, int col) {
        return memory.readLong(row * recordLength + col * BitBuddy.LONG_BYTES);
    }

    public void writeRow(long row, long[] data) {
        if (data.length != columns) {
            throw new IndexOutOfBoundsException(Strings.apply("%s != %s", data.length, columns));
        }

        long baseAddress = row * recordLength;
        for (int i = 0; i < columns; i++) {
            memory.writeLong(baseAddress + i * 8, data[i]);
        }
    }

    public void readRow(long row, long[] dst) {
        if (dst.length != columns) {
            throw new IndexOutOfBoundsException(Strings.apply("%s != %s", dst.length, columns));
        }

        long baseAddress = row * recordLength;
        for (int i = 0; i < columns; i++) {
            dst[i] = memory.readLong(baseAddress + i * 8);
        }
    }

    public long getNumberOfRows() {
        return numberOfRows.get();
    }

    @Override
    public String toString() {
        return "Rows: " + numberOfRows.get() + " with " + columns + " columns";
    }

    @Override
    public void release() {
        memory.release();
    }
}
