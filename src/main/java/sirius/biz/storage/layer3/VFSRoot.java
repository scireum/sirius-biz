/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.kernel.di.std.Priorized;

/**
 * Represents a root within the {@link VirtualFileSystem}.
 * <p>
 * This can contribute one or more top-level directories for the VFS. Implementing classes must wear a {@link
 * sirius.kernel.di.std.Register} to become visible to the injector and the VFS framework.
 */
public interface VFSRoot extends ChildProvider, Priorized {

}
