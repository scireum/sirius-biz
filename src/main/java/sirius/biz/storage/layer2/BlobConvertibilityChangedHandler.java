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
 * Defines handlers to process {@link Blob blobs} after their {@link Blob#isInconvertible() convertibility status} changed.
 *
 * @see ProcessBlobChangesLoop
 */
public interface BlobConvertibilityChangedHandler extends Priorized {
    /**
     * Executed when a blob's inconvertible flag changed.
     *
     * @param blob the modified blob
     */
    void execute(@Nonnull Blob blob);
}
