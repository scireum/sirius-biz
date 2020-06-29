/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * Implements a spliterator which is used to stream through sub trees of the VFS via {@link VirtualFile#tree()} etc.
 */
class VirtualFileWalker implements Spliterator<VirtualFile> {

    private TreeVisitorBuilder settings;
    private LinkedList<Iterator<VirtualFile>> stack = new LinkedList<>();
    private Iterator<VirtualFile> children;
    private int filesScanned = 0;

    VirtualFileWalker(VirtualFile rootFile, TreeVisitorBuilder settings) {
        this.settings = settings;

        if (settings.subTreeOnly) {
            settings.maxDepth -= 1;
            this.children = new BlockwiseIterator(rootFile);
        } else {
            this.children = Collections.singletonList(rootFile).iterator();
        }
    }

    @Override
    public boolean tryAdvance(Consumer<? super VirtualFile> action) {
        while (true) {
            if (!prepareNext()) {
                return false;
            }

            VirtualFile next = children.next();
            if (shouldProcessAsFile(next)) {
                action.accept(next);
                return scanCompleted();
            }

            if (shouldVisitDirectory(next)) {
                if (shouldEnterDirectory()) {
                    stack.add(children);
                    children = new BlockwiseIterator(next);
                }
                if (shouldProcessAsDirectory()) {
                    action.accept(next);
                    return scanCompleted();
                }
            }
        }
    }

    private boolean prepareNext() {
        if (children.hasNext()) {
            return true;
        }
        if (stack.isEmpty()) {
            return false;
        }

        children = stack.removeLast();
        return true;
    }

    private boolean shouldProcessAsFile(VirtualFile next) {
        return !next.isDirectory() && !settings.excludeFiles;
    }

    private boolean shouldVisitDirectory(VirtualFile next) {
        return next.isDirectory() && (settings.directoryFilter == null || settings.directoryFilter.test(next));
    }

    private boolean shouldEnterDirectory() {
        return settings.maxDepth < 0 || stack.size() < settings.maxDepth;
    }

    private boolean shouldProcessAsDirectory() {
        return !settings.excludeDirectories;
    }

    private boolean scanCompleted() {
        return settings.maxFiles <= 0 || filesScanned++ < settings.maxFiles;
    }

    @Override
    public Spliterator<VirtualFile> trySplit() {
        return null;
    }

    @Override
    public long estimateSize() {
        return settings.maxFiles > 0 ? settings.maxFiles : Long.MAX_VALUE;
    }

    @Override
    public int characteristics() {
        return Spliterator.DISTINCT | Spliterator.NONNULL;
    }
}
