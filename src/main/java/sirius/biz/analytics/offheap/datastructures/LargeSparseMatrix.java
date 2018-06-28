/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.offheap.datastructures;

import sirius.biz.analytics.offheap.memory.Allocator;
import sirius.biz.analytics.offheap.memory.LargeMemoryPool;
import sirius.biz.analytics.offheap.memory.LargeResource;

import java.util.function.BiConsumer;
import java.util.function.Function;

public class LargeSparseMatrix implements LargeResource {

    private LargeMultiBTree tree;

    public LargeSparseMatrix(LargeMemoryPool pool, String name) {
        tree = new LargeMultiBTree(new Allocator(pool, "LargeSparseMatrix: " + name, this), 2, 32, false);
    }

    public LargeSparseMatrix(Allocator allocator) {
        tree = new LargeMultiBTree(allocator, 2, 32, false);
    }

    public void put(long x, long y, long value) {
        if (value != 0) {
            tree.put(new long[]{x, y}, value);
        }
    }

    public void update(long x, long y, Function<Long, Long> updater) {
        tree.put(new long[]{x, y}, updater);
    }

    public long get(long x, long y) {
        return tree.get(new long[]{x, y}).orElse(0L);
    }

    public void iterateRow(long x, BiConsumer<Long, Long> yAndValueConsumer) {
        tree.iterate(new long[]{x, 0}, (key, value) -> {
            if (key[0] != x) {
                return false;
            }

            yAndValueConsumer.accept(key[1], value);
            return true;
        });
    }

    @Override
    public void release() {
        tree.release();
    }
}
