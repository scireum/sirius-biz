/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.offheap.memory;

import java.util.concurrent.ConcurrentHashMap;

public class LargeMemoryPool {

    private ConcurrentHashMap<String, Allocator> allocators = new ConcurrentHashMap<>();
    private String name;

    public LargeMemoryPool(String name) {
        this.name = name;
    }

    public void register(String name, Allocator allocator) {
        allocators.put(name, allocator);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        sb.append("\n-------------------\n");
        for (Allocator alloc : allocators.values()) {
            sb.append(alloc).append("\n");
        }
        sb.append("-------------------\n");

        return sb.toString();
    }

    public void release() {
        allocators.values().forEach(Allocator::release);
    }
}
