/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Implements a spliterator which is used to stream through sub trees of the VFS via {@link VirtualFile#tree()} etc.
 */
class VirtualFileWalker implements Spliterator<VirtualFile> {

    private Predicate<VirtualFile> directoryFilter;
    private List<Iterator<VirtualFile>> stack;
    private Iterator<VirtualFile> children;

    VirtualFileWalker(List<VirtualFile> children, Predicate<VirtualFile> directoryFilter) {
        this.children = children.iterator();
        this.directoryFilter = directoryFilter;
    }

    @Override
    public boolean tryAdvance(Consumer<? super VirtualFile> action) {
        while (true) {
            if (!children.hasNext()) {
                if (stack.isEmpty()) {
                    return false;
                } else {
                    children = stack.remove(stack.size() - 1);
                }
            }

            VirtualFile next = children.next();
            if (!next.isDirectory()) {
                action.accept(next);
                return true;
            }

            if (directoryFilter.test(next)) {
                stack.add(children);
                children = next.allChildren().iterator();
                action.accept(next);
                return true;
            }
        }
    }

    @Override
    public Spliterator<VirtualFile> trySplit() {
        return null;
    }

    @Override
    public long estimateSize() {
        return Long.MAX_VALUE;
    }

    @Override
    public int characteristics() {
        return Spliterator.DISTINCT | Spliterator.NONNULL;
    }
}
