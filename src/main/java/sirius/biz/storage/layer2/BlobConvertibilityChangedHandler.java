/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

/**
 * Defines handlers to process {@link Blob blobs} after their {@link Blob#isInconvertible() convertibility status} changed.
 * <p>
 * Note: Handlers need to be {@link sirius.kernel.di.std.Register registered}.
 *
 * @see ProcessBlobChangesLoop
 */
public interface BlobConvertibilityChangedHandler extends BlobChangedHandler {
}
