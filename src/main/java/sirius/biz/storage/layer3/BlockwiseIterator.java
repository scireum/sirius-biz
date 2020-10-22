/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.kernel.commons.Limit;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Iterates over the children of a <tt>VirtualFile</tt> in a blockwise manner.
 * <p>
 * This permits to safely process even very large directories with the downside that a file might
 * be skipped or processed twice if a concurrent iteration happens (which shoud almost never be the case).
 */
class BlockwiseIterator implements Iterator<VirtualFile> {

    private static final int BLOCK_SIZE = 1000;

    private VirtualFile virtualFile;
    private int nextStart = 0;
    private Iterator<VirtualFile> currentBlock;

    protected BlockwiseIterator(VirtualFile virtualFile) {
        this.virtualFile = virtualFile;
        this.currentBlock = fetchNextBlock();
    }

    private Iterator<VirtualFile> fetchNextBlock() {
        List<VirtualFile> buffer = new ArrayList<>();
        virtualFile.children(new FileSearch(buffer::add).withLimit(new Limit(nextStart, BLOCK_SIZE)));
        if (buffer.isEmpty()) {
            return null;
        }

        nextStart += buffer.size();
        return buffer.iterator();
    }

    @Override
    public boolean hasNext() {
        if (currentBlock != null && !currentBlock.hasNext()) {
            currentBlock = fetchNextBlock();
        }

        return currentBlock != null && currentBlock.hasNext();
    }

    @Override
    public VirtualFile next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        return currentBlock.next();
    }
}
