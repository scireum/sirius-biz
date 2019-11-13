/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Priorized;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Provides a base implementation for vfs roots which (at most) provide a single root directory.
 */
public abstract class SingularVFSRoot implements VFSRoot {

    /**
     * Determines if the directory should be shown based on the current user etc.
     *
     * @return <tt>true</tt> if the root directory should be visible, <tt>false</tt> otherwise
     */
    protected abstract boolean isEnabled();

    /**
     * Returns the name of the root directory.
     *
     * @return the name of the root directory
     */
    protected abstract String getName();

    /**
     * Returns a short (one line) description of the purpose of this directory.
     *
     * @return a short and hopefully translated description of this directory
     */
    @Nullable
    protected String getDescription() {
        return null;
    }

    @Override
    public Optional<VirtualFile> findChild(VirtualFile parent, String name) {
        if (Strings.areEqual(name, getName()) && isEnabled()) {
            return Optional.of(makeRoot(parent));
        }
        return Optional.empty();
    }

    protected VirtualFile makeRoot(VirtualFile parent) {
        MutableVirtualFile result = new MutableVirtualFile(parent, getName());
        result.markAsExistingDirectory();
        result.withDescription(getDescription());
        populateRoot(result);

        return result;
    }

    /**
     * Populates the generated root directory.
     *
     * @param rootDirectory the directory to populate with callbacks
     */
    protected abstract void populateRoot(MutableVirtualFile rootDirectory);

    @Override
    public void enumerate(VirtualFile parent, FileSearch search) {
        if (isEnabled()) {
            search.processResult(makeRoot(parent));
        }
    }

    @Override
    public int getPriority() {
        return Priorized.DEFAULT_PRIORITY;
    }
}
