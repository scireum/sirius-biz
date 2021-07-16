/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.kernel.commons.ValueHolder;

import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Helps to configure how a directory tree is traversed.
 * <p>
 * This is used by {@link VirtualFile#tree()} and permits to provide a clean and fluent API.
 */
public class TreeVisitorBuilder {

    protected boolean subTreeOnly = false;
    protected int maxFiles = 0;
    protected int maxDepth = -1;
    protected boolean excludeDirectories = false;
    protected boolean excludeFiles = false;
    protected Predicate<VirtualFile> directoryFilter;
    private final VirtualFile file;

    protected TreeVisitorBuilder(VirtualFile file) {
        this.file = file;
    }

    /**
     * Only process child files but not to root file on which {@link VirtualFile#tree()} was called.
     *
     * @return the builder itself for fluent method calls
     */
    public TreeVisitorBuilder subTreeOnly() {
        this.subTreeOnly = true;
        return this;
    }

    /**
     * Limits the amount of files to visit.
     *
     * @param maxFilesToVisit the maximal number of <tt>VirtualFiles</tt> (which can be either a file or a directory)
     *                        to visit
     * @return the builder itself for fluent method calls
     */
    public TreeVisitorBuilder limit(int maxFilesToVisit) {
        this.maxFiles = maxFilesToVisit;
        return this;
    }

    /**
     * Sets the maximal depth to visit.
     *
     * @param maxDepthToVisit the maximal depth where e.g. <tt>1</tt> would be "only direct children"
     * @return the builder itself for fluent method calls
     */
    public TreeVisitorBuilder maxDepth(int maxDepthToVisit) {
        this.maxDepth = maxDepthToVisit;
        return this;
    }

    /**
     * Boilerplate method to only visit direct children.
     * <p>
     * This is short for {@code .subTreeOnly().maxDepth(1)}.
     *
     * @return the builder itself for fluent method calls
     */
    public TreeVisitorBuilder directChildrenOnly() {
        return subTreeOnly().maxDepth(1);
    }

    /**
     * Determines if directories should be excluded from the iterator (directories will still be traversed,
     * just not reported).
     *
     * @return the builder itself for fluent method calls
     */
    public TreeVisitorBuilder excludeDirectories() {
        this.excludeDirectories = true;
        return this;
    }

    /**
     * Determines if files should be excluded from the iterator.
     *
     * @return the builder itself for fluent method calls
     */
    public TreeVisitorBuilder excludeFiles() {
        this.excludeFiles = true;
        return this;
    }

    /**
     * Determines if a directory should be visited.
     * <p>
     * This this returns <tt>false</tt>, the directory will be neither traversed, nor reported by the iterator.
     *
     * @param directoryFilter the filter which returns <tt>true</tt> to signal that the directory should be visited
     *                        or <tt>false</tt> otherwise
     * @return <
     */
    public TreeVisitorBuilder withDirectoryFilter(Predicate<VirtualFile> directoryFilter) {
        this.directoryFilter = directoryFilter;
        return this;
    }

    /**
     * Iterates over all <tt>VirtualFiles</tt> matching the filters specified by this builder.
     * <p>
     * Note that this will perform a DFS (depth first search).
     *
     * @param acceptanceFunction invoked for each matching file. Returns <tt>true</tt> to continue traversing the
     *                           directory tree or <tt>false</tt> to abort
     */
    public void iterate(Predicate<VirtualFile> acceptanceFunction) {
        if (subTreeOnly && maxDepth == 0) {
            return;
        }

        VirtualFileWalker fileWalker = new VirtualFileWalker(file, this);
        ValueHolder<VirtualFile> buffer = ValueHolder.of(null);
        boolean shouldContinue = true;
        while (shouldContinue) {
            shouldContinue = fileWalker.tryAdvance(buffer);
            if (shouldContinue) {
                shouldContinue = acceptanceFunction.test(buffer.get());
            }
        }
    }

    /**
     * Provides a stream over all matching  <tt>VirtualFiles</tt> for the filters specified by this builder.
     *
     * @return a stream which perfroms a DFS (depth first search) directory traveral of the files matching the given
     * filters
     */
    public Stream<VirtualFile> stream() {
        if (subTreeOnly && maxDepth == 0) {
            return Stream.empty();
        }

        return StreamSupport.stream(new VirtualFileWalker(file, this), false);
    }
}
