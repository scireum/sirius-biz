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
 * Defines handlers to process created {@link Blob blobs}.
 * <p>
 * Note: Handlers need to be {@link sirius.kernel.di.std.Register registered}.
 * <p>
 * Have in mind that blobs being processed by this handler might not have yet a physical object, since blob creation
 * and actual upload of its binary contents happens in separate events.
 *
 * @see ProcessBlobChangesLoop
 */
@AutoRegister
public interface BlobCreatedHandler extends BlobChangedHandler {
}
