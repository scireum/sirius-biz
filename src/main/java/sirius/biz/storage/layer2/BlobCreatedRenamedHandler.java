/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.kernel.di.std.Priorized;

import javax.annotation.Nonnull;

/**
 * Defines handlers to process created or modified {@link sirius.biz.storage.layer2.Blob blobs}.
 *
 * @see ProcessBlobChangesLoop
 */
public interface BlobCreatedRenamedHandler extends Priorized {
    /**
     * Executed when a blob is inserted or some of its metadata (such as file name) is modified.
     *
     * @param blob the modified blob
     */
    void execute(@Nonnull Blob blob);
}
