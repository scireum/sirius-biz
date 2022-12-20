/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

/**
 * Defines handlers to process created or modified {@link sirius.biz.storage.layer2.Blob blobs}.
 * <p>
 * Note: Handlers need to be {@link sirius.kernel.di.std.Register registered}.
 *
 * @see ProcessBlobChangesLoop
 */
public interface BlobCreatedRenamedHandler extends BlobChangedHandler {
}
