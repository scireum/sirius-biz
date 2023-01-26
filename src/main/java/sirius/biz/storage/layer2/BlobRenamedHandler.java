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
 * Defines handlers to process renamed {@link Blob blobs}.
 * <p>
 * Note: Handlers need to be {@link sirius.kernel.di.std.Register registered}.
 *
 * @see ProcessBlobChangesLoop
 */
@AutoRegister
public interface BlobRenamedHandler extends BlobChangedHandler {
}
