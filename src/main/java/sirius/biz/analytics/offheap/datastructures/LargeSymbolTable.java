/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.offheap.datastructures;

import com.google.common.base.Charsets;
import sirius.biz.analytics.offheap.memory.Allocator;
import sirius.biz.analytics.offheap.memory.BitBuddy;
import sirius.biz.analytics.offheap.memory.LargeMemory;
import sirius.biz.analytics.offheap.memory.LargeMemoryPool;
import sirius.biz.analytics.offheap.memory.LargeResource;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Watch;

public class LargeSymbolTable implements LargeResource {

    private LargeMemory lookupHash;
    private LargeMemory stringData;
    private long hashSlots;
    private long usedSlots;

    public LargeSymbolTable(LargeMemoryPool pool, String name) {
        initMemory(new Allocator(pool, "LargeSymbolTable: " + name, this));
    }

    public LargeSymbolTable(Allocator allocator) {
        initMemory(allocator);
    }

    protected void initMemory(Allocator allocator) {
        lookupHash = new LargeMemory(allocator);
        lookupHash.alloc(LargeMemory.PAGE_SIZE);
        stringData = new LargeMemory(allocator);
        hashSlots = lookupHash.getUsedSize() / BitBuddy.LONG_BYTES;
    }

    public long symbol(String symbol) {
        long hash = Math.abs(symbol.hashCode());
        byte[] data = symbol.getBytes(Charsets.UTF_8);

        return lookup(hash, data);
    }

    protected long lookup(long hash, byte[] data) {
        long index = (hash % hashSlots) * BitBuddy.LONG_BYTES;
        while (true) {
            long dataIndex = lookupHash.readLong(index);
            if (dataIndex == 0) {
                dataIndex = stringData.alloc(data.length + BitBuddy.INT_BYTES + BitBuddy.LONG_BYTES);
                stringData.writeInt(dataIndex, data.length);
                stringData.writeLong(dataIndex + BitBuddy.INT_BYTES, hash);
                stringData.writeBytes(dataIndex + BitBuddy.INT_BYTES + BitBuddy.LONG_BYTES, data, data.length);
                lookupHash.writeLong(index, dataIndex);
                usedSlots++;
                if (hashSlots * 0.75f < usedSlots) {
                    rehash();
                }

                return dataIndex;
            }
            if (compare(dataIndex, data)) {
                return dataIndex;
            }

            index += BitBuddy.LONG_BYTES;
            if (index >= lookupHash.getUsedSize()) {
                index -= lookupHash.getUsedSize();
            }
        }
    }

    private void rehash() {
        Watch w = Watch.start();
        lookupHash.zeroAll();
        lookupHash.alloc(LargeMemory.PAGE_SIZE);
        hashSlots += LargeMemory.PAGE_SIZE / BitBuddy.LONG_BYTES;
        long index = 0;
        while (index < stringData.getUsedSize()) {
            int length = stringData.readInt(index);
            long hash = stringData.readLong(index + BitBuddy.INT_BYTES);
            rehashPair(index, hash);
            index += BitBuddy.INT_BYTES + BitBuddy.LONG_BYTES + length;
        }
        System.out.println("rehasing: " + w.duration() + " " + usedSlots + "/" + hashSlots);
    }

    private void rehashPair(long index, long hash) {
        long lookupIndex = (hash % hashSlots) * BitBuddy.LONG_BYTES;
        while (true) {
            long dataIndex = lookupHash.readLong(lookupIndex);
            if (dataIndex == 0) {
                lookupHash.writeLong(lookupIndex, index);
                return;
            }

            lookupIndex += BitBuddy.LONG_BYTES;
            if (lookupIndex >= lookupHash.getUsedSize()) {
                lookupIndex -= lookupHash.getUsedSize();
            }
        }
    }

    private boolean compare(long dataIndex, byte[] data) {
        int length = stringData.readInt(dataIndex);
        if (length != data.length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (stringData.readByte(dataIndex + BitBuddy.INT_BYTES + BitBuddy.LONG_BYTES + i) != data[i]) {
                return false;
            }
        }

        return true;
    }

    public String string(long symbol) {
        int length = stringData.readInt(symbol);
        byte[] data = new byte[length];
        stringData.readBytes(symbol + BitBuddy.INT_BYTES + BitBuddy.LONG_BYTES, data, 0, length);

        return new String(data, Charsets.UTF_8);
    }

    @Override
    public void release() {
        stringData.release();
        lookupHash.release();
    }

    @Override
    public String toString() {
        return Strings.apply("Symbols: %s (Lookup-Table: %s / Symbol-Data: %s)", usedSlots, lookupHash, stringData);
    }
}
