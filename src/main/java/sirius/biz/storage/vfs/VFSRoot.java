/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.vfs;

import java.util.function.Consumer;

/**
 * Represents a root withing the {@link VirtualFileSystem}.
 * <p>
 * This can contribute one of more top-level files for the VFS. Implementing classes must wear a {@link
 * sirius.kernel.di.std.Register} to become visible to the injector and the VFS framework.
 */
public interface VFSRoot {

    /**
     * Collects all top-level files provided by this root.
     *
     * @param parent   the root directory of the VFS to be used a parent for the provided files
     * @param consumer the consumer to collect all provided files
     */
    void collectRootFolders(VirtualFile parent, Consumer<VirtualFile> consumer);
}
