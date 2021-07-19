/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.vfs;

import java.util.function.Consumer;

/**
 * Represents a root withing the {@link sirius.biz.storage.layer3.VirtualFileSystem}.
 * <p>
 * This can contribute one of more top-level files for the VFS. Implementing classes must wear a {@link
 * sirius.kernel.di.std.Register} to become visible to the injector and the VFS framework.
 *
 * @deprecated Replaced by {@link sirius.biz.storage.layer3.VFSRoot}
 */
@Deprecated
public interface VFSRoot {

    /**
     * Collects all top-level files provided by this root.
     *
     * @param parent   the root directory of the VFS to be used a parent for the provided files
     * @param fileCollector the consumer to collect all provided files
     */
    void collectRootFolders(VirtualFile parent, Consumer<VirtualFile> fileCollector);
}
