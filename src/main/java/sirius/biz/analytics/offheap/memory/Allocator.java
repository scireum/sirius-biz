/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.offheap.memory;

import sirius.kernel.nls.NLS;

import java.util.concurrent.atomic.AtomicLong;

public class Allocator {

    private final LargeMemoryPool pool;
    private final LargeResource resource;
    private final String name;
    private final AtomicLong allocatedBytes = new AtomicLong();

    public Allocator(LargeMemoryPool pool, String name, LargeResource resource) {
        this.pool = pool;
        this.name = name;
        this.resource = resource;
        this.pool.register(name, this);
    }

    protected void release() {
        this.resource.release();
    }

    protected long alloc(long size) {
        allocatedBytes.addAndGet(size);
        return LargeMemory.UNSAFE.allocateMemory(size);
    }

    protected void free(long addr, long size) {
        allocatedBytes.addAndGet(-size);
        LargeMemory.UNSAFE.freeMemory(addr);
    }

    @Override
    public String toString() {
        return name + " - " + resource + " (Allocated: " + NLS.formatSize(allocatedBytes.get()) + ")";
    }
}
