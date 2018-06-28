/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.offheap.memory;

import sirius.kernel.commons.Strings;
import sirius.kernel.nls.NLS;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicLong;

public class LargeMemory {

    public static final int PAGE_SIZE = 4 * 1024 * 1024;
    private static final boolean BIG_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;

    protected static final Unsafe UNSAFE;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (Unsafe) field.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private AtomicLong usedSize = new AtomicLong();
    private volatile long allocatedSize = 0;
    private volatile int numberOfBuffers = 0;
    private volatile long[] buffers = new long[16];
    private Allocator allocator;

    public LargeMemory(Allocator allocator) {
        this.allocator = allocator;
    }

    public void writeByte(long address, byte data) {
        ensureAllocation(address);

        int bufferIndex = (int) (address / PAGE_SIZE);
        int bufferOffset = (int) (address % PAGE_SIZE);

        UNSAFE.putByte(buffers[bufferIndex] + bufferOffset, data);
    }

    protected void ensureAllocation(long address) {
        if (address < 0) {
            throw new IndexOutOfBoundsException(Strings.apply("%s <0", address));
        }
        if (address >= allocatedSize) {
            throw new IndexOutOfBoundsException(Strings.apply("%s > %s", address, allocatedSize));
        }
    }

    public void writeBytes(long address, byte[] data, int length) {
        ensureAllocation(address + length - 1);

        int bufferIndex = (int) (address / PAGE_SIZE);
        int bufferOffset = (int) (address % PAGE_SIZE);

        for (int i = 0; i < length; i++) {
            UNSAFE.putByte(buffers[bufferIndex] + bufferOffset, data[i]);
            bufferOffset++;
            if (bufferOffset >= PAGE_SIZE) {
                bufferOffset = 0;
                bufferIndex++;
            }
        }
    }

    public void zero(long address, int numberOfBytes) {
        ensureAllocation(address + numberOfBytes - 1);

        int bufferIndex = (int) (address / PAGE_SIZE);
        int bufferOffset = (int) (address % PAGE_SIZE);

        if (bufferOffset >= PAGE_SIZE - numberOfBytes) {
            zeroCrossBoundary(numberOfBytes, bufferIndex, bufferOffset);
        } else {
            UNSAFE.setMemory(buffers[bufferIndex] + bufferOffset, numberOfBytes, (byte) 0);
        }
    }

    public void transferBytes(long sourceAddress, long destAddress, int numberOfBytes) {
        ensureAllocation(sourceAddress + numberOfBytes - 1);
        ensureAllocation(destAddress + numberOfBytes - 1);

        int srcBufferIndex = (int) (sourceAddress / PAGE_SIZE);
        int srcBufferOffset = (int) (sourceAddress % PAGE_SIZE);
        int destBufferIndex = (int) (destAddress / PAGE_SIZE);
        int destBufferOffset = (int) (destAddress % PAGE_SIZE);

        long srcAddr = buffers[srcBufferIndex] + srcBufferOffset;
        long destAddr = buffers[destBufferIndex] + destBufferOffset;

        if ((destAddr > srcAddr + numberOfBytes || srcAddr > destAddr + numberOfBytes)
            && srcBufferOffset < PAGE_SIZE - numberOfBytes
            && destBufferOffset < PAGE_SIZE - numberOfBytes) {
            // Target regions are both within a contiuous region of memory and do not overlap,
            // lets memcpy...
            UNSAFE.copyMemory(srcAddr, destAddr, numberOfBytes);
        } else {
            complexTransfer(sourceAddress,
                            destAddress,
                            numberOfBytes,
                            srcBufferIndex,
                            srcBufferOffset,
                            destBufferIndex,
                            destBufferOffset);
        }
    }

    private void complexTransfer(long sourceAddress,
                                 long destAddress,
                                 long numberOfBytes,
                                 int srcBufferIndex,
                                 int srcBufferOffset,
                                 int destBufferIndex,
                                 int destBufferOffset) {
        if (sourceAddress < destAddress && sourceAddress + numberOfBytes > destAddress) {
            transferOverlappingRegions(numberOfBytes,
                                       srcBufferIndex,
                                       srcBufferOffset,
                                       destBufferIndex,
                                       destBufferOffset);
        } else {
            transferCrossBoundaries(numberOfBytes, srcBufferIndex, srcBufferOffset, destBufferIndex, destBufferOffset);
        }
    }

    private void transferCrossBoundaries(long numberOfBytes,
                                         int srcBufferIndex,
                                         int srcBufferOffset,
                                         int destBufferIndex,
                                         int destBufferOffset) {
        for (long i = 0; i < numberOfBytes; i++) {
            UNSAFE.putByte(buffers[destBufferIndex] + destBufferOffset,
                           UNSAFE.getByte(buffers[srcBufferIndex] + srcBufferOffset));
            srcBufferOffset++;
            if (srcBufferOffset < 0) {
                srcBufferOffset = PAGE_SIZE - 1;
                srcBufferIndex--;
            }
            destBufferOffset++;
            if (destBufferOffset < 0) {
                destBufferOffset = PAGE_SIZE - 1;
                destBufferIndex--;
            }
        }
    }

    private void transferOverlappingRegions(long numberOfBytes,
                                            int srcBufferIndex,
                                            int srcBufferOffset,
                                            int destBufferIndex,
                                            int destBufferOffset) {
        srcBufferOffset += numberOfBytes - 1;
        while (srcBufferOffset >= PAGE_SIZE) {
            srcBufferOffset -= PAGE_SIZE;
            srcBufferIndex++;
        }
        destBufferOffset += numberOfBytes - 1;
        while (destBufferOffset >= PAGE_SIZE) {
            destBufferOffset -= PAGE_SIZE;
            destBufferIndex++;
        }

        for (long i = 0; i < numberOfBytes; i++) {
            UNSAFE.putByte(buffers[destBufferIndex] + destBufferOffset,
                           UNSAFE.getByte(buffers[srcBufferIndex] + srcBufferOffset));
            srcBufferOffset--;
            if (srcBufferOffset < 0) {
                srcBufferOffset = PAGE_SIZE - 1;
                srcBufferIndex--;
            }
            destBufferOffset--;
            if (destBufferOffset < 0) {
                destBufferOffset = PAGE_SIZE - 1;
                destBufferIndex--;
            }
        }
    }

    protected void zeroCrossBoundary(long numberOfBytes, int bufferIndex, int bufferOffset) {
        for (int i = 0; i < numberOfBytes; i++) {
            UNSAFE.putByte(buffers[bufferIndex] + bufferOffset, (byte) 0);
            bufferOffset++;
            if (bufferOffset >= PAGE_SIZE) {
                bufferOffset = 0;
                bufferIndex++;
            }
        }
    }

    public void writeInt(long address, int data) {
        ensureAllocation(address + 3);

        int bufferOffset = (int) (address % PAGE_SIZE);
        if (bufferOffset >= PAGE_SIZE - 4) {
            writeIntCrossBoundary(address, data);
        } else {
            int bufferIndex = (int) (address / PAGE_SIZE);
            UNSAFE.putInt(buffers[bufferIndex] + bufferOffset, data);
        }
    }

    protected void writeIntCrossBoundary(long address, int data) {
        if (BIG_ENDIAN) {
            writeBytes(address,
                       new byte[]{(byte) (data & 0xFF),
                                  (byte) ((data & 0xFF00) >> 8),
                                  (byte) ((data & 0xFF0000) >> 16),
                                  (byte) ((data & 0xFF000000) >> 24)},
                       4);
        } else {
            writeBytes(address,
                       new byte[]{(byte) ((data & 0xFF000000) >> 24),
                                  (byte) ((data & 0xFF0000) >> 16),
                                  (byte) ((data & 0xFF00) >> 8),
                                  (byte) (data & 0xFF)},
                       4);
        }
    }

    public void writeLong(long address, long data) {
        ensureAllocation(address + 7);

        int bufferOffset = (int) (address % PAGE_SIZE);
        if (bufferOffset >= PAGE_SIZE - 8) {
            writeLongCrossBoundary(address, data);
        } else {
            int bufferIndex = (int) (address / PAGE_SIZE);
            UNSAFE.putLong(buffers[bufferIndex] + bufferOffset, data);
        }
    }

    protected void writeLongCrossBoundary(long address, long data) {
        if (BIG_ENDIAN) {
            writeBytes(address,
                       new byte[]{(byte) (data & 0xFFL),
                                  (byte) ((data & 0xFF00L) >> 8),
                                  (byte) ((data & 0xFF0000L) >> 16),
                                  (byte) ((data & 0xFF000000L) >> 24),
                                  (byte) ((data & 0xFF00000000L) >> 32),
                                  (byte) ((data & 0xFF0000000000L) >> 40),
                                  (byte) ((data & 0xFF000000000000L) >> 48),
                                  (byte) ((data & 0xFF00000000000000L) >> 56)},
                       8);
        } else {
            writeBytes(address,
                       new byte[]{(byte) ((data & 0xFF00000000000000L) >> 56),
                                  (byte) ((data & 0xFF000000000000L) >> 48),
                                  (byte) ((data & 0xFF0000000000L) >> 40),
                                  (byte) ((data & 0xFF00000000L) >> 32),
                                  (byte) ((data & 0xFF000000) >> 24),
                                  (byte) ((data & 0xFF0000) >> 16),
                                  (byte) ((data & 0xFF00) >> 8),
                                  (byte) (data & 0xFF)},
                       8);
        }
    }

    public byte readByte(long address) {
        ensureAllocation(address);

        int bufferIndex = (int) (address / PAGE_SIZE);
        int bufferOffset = (int) (address % PAGE_SIZE);

        return UNSAFE.getByte(buffers[bufferIndex] + bufferOffset);
    }

    public void readBytes(long address, byte[] dst, int offset, int length) {
        ensureAllocation(address + length - 1);

        int bufferIndex = (int) (address / PAGE_SIZE);
        int bufferOffset = (int) (address % PAGE_SIZE);

        for (int i = 0; i < length; i++) {
            dst[i + offset] = UNSAFE.getByte(buffers[bufferIndex] + bufferOffset);
            bufferOffset++;
            if (bufferOffset >= PAGE_SIZE) {
                bufferOffset = 0;
                bufferIndex++;
            }
        }
    }

    public int readInt(long address) {
        ensureAllocation(address + 3);

        int bufferOffset = (int) (address % PAGE_SIZE);
        if (bufferOffset >= PAGE_SIZE - 4) {
            return readIntCrossBoundary(address);
        } else {
            int bufferIndex = (int) (address / PAGE_SIZE);
            return UNSAFE.getInt(buffers[bufferIndex] + bufferOffset);
        }
    }

    protected int readIntCrossBoundary(long address) {
        byte[] dst = new byte[4];
        readBytes(address, dst, 0, 4);
        if (BIG_ENDIAN) {
            return (dst[0] & 0xFF) | (dst[1] & 0xFF) << 8 | (dst[2] & 0xFF) << 16 | (dst[3] & 0xFF) << 24;
        } else {
            return (dst[0] & 0xFF) << 24 | (dst[1] & 0xFF) << 16 | (dst[2] & 0xFF) << 8 | (dst[3] & 0xFF);
        }
    }

    public long readLong(long address) {
        ensureAllocation(address + 7);

        int bufferOffset = (int) (address % PAGE_SIZE);
        if (bufferOffset >= PAGE_SIZE - 8) {
            return readLongCrossBoundary(address);
        } else {
            int bufferIndex = (int) (address / PAGE_SIZE);
            return UNSAFE.getLong(buffers[bufferIndex] + bufferOffset);
        }
    }

    protected long readLongCrossBoundary(long address) {
        byte[] dst = new byte[8];
        readBytes(address, dst, 0, 8);
        if (BIG_ENDIAN) {
            return (dst[0] & 0xFF)
                   | (long) (dst[1] & 0xFF) << 8
                   | (long) (dst[2] & 0xFF) << 16
                   | (long) (dst[3] & 0xFF) << 24
                   | (long) (dst[4] & 0xFF) << 32
                   | (long) (dst[5] & 0xFF) << 40
                   | (long) (dst[6] & 0xFF) << 48
                   | (long) (dst[7] & 0xFF) << 56;
        } else {
            return (long) (dst[0] & 0xFF) << 56
                   | (long) (dst[1] & 0xFF) << 48
                   | (long) (dst[2] & 0xFF) << 40
                   | (long) (dst[3] & 0xFF) << 32
                   | (long) (dst[4] & 0xFF) << 24
                   | (long) (dst[5] & 0xFF) << 16
                   | (long) (dst[6] & 0xFF) << 8
                   | (dst[7] & 0xFF);
        }
    }

    public long getUsedSize() {
        return usedSize.get();
    }

    public long alloc(long numberOfBytes) {
        long expectedUsed = usedSize.get();
        while (!alloc(expectedUsed, numberOfBytes)) {
            expectedUsed = usedSize.get();
        }

        return expectedUsed;
    }

    public boolean alloc(long expectedUsedSize, long numberOfBytes) {
        if (expectedUsedSize + numberOfBytes <= allocatedSize) {
            // If we have enough ram allocated already, try to simply alloc a piece of it (using optimistic locking)
            return usedSize.compareAndSet(expectedUsedSize, expectedUsedSize + numberOfBytes);
        }

        // We need more ram! Lets acquire a real lock...
        return allocSynced(expectedUsedSize, numberOfBytes);
    }

    private synchronized boolean allocSynced(long expectedUsedSize, long numberOfBytes) {
        // See if a previous call already allocated enough ram...
        if (expectedUsedSize + numberOfBytes <= allocatedSize) {
            return usedSize.compareAndSet(expectedUsedSize, expectedUsedSize + numberOfBytes);
        }

        // Increment the used size up above the allocated size - this will make all alloc calls
        // synchronized from this point on...
        if (!usedSize.compareAndSet(expectedUsedSize, expectedUsedSize + numberOfBytes)) {
            return false;
        }

        // Now that we're on our own - let's do the heavy lifting...
        long bytesToAllocate = numberOfBytes - (allocatedSize - expectedUsedSize);
        while (bytesToAllocate > 0) {
            if (numberOfBuffers >= buffers.length - 1) {
                long[] newBuffers = new long[buffers.length + 16];
                System.arraycopy(buffers, 0, newBuffers, 0, buffers.length);
                buffers = newBuffers;
            }

            long bufferAddress = allocator.alloc(PAGE_SIZE);
            buffers[numberOfBuffers++] = bufferAddress;
            UNSAFE.setMemory(bufferAddress, PAGE_SIZE, (byte) 0);
            bytesToAllocate -= PAGE_SIZE;
            allocatedSize += PAGE_SIZE;
        }

        return true;
    }

    @Override
    public String toString() {
        return NLS.formatSize(getUsedSize()) + " in " + numberOfBuffers + " buffers";
    }

    public synchronized void release() {
        allocatedSize = 0;
        usedSize.set(0);

        for (int i = 0; i < numberOfBuffers; i++) {
            allocator.free(buffers[i], PAGE_SIZE);
        }

        numberOfBuffers = 0;
    }

    public void zeroAll() {
        for (int i = 0; i < numberOfBuffers; i++) {
            UNSAFE.setMemory(buffers[i], PAGE_SIZE, (byte) 0);
        }
    }
}
