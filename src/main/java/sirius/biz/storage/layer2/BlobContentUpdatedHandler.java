/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.kernel.di.std.AutoRegister;

/**
 * Defines handlers to process {@linkplain Blob blobs} which content (the underlying physical object) has been updated.
 * <p>
 * Note: Handlers need to be {@linkplain sirius.kernel.di.std.Register registered}.
 *
 * @see ProcessBlobChangesLoop
 */
@AutoRegister
public interface BlobContentUpdatedHandler extends BlobChangedHandler {
}
