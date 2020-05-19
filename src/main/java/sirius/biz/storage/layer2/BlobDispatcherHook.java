/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.kernel.di.std.Named;

import javax.annotation.Nullable;

/**
 * Gets invoked once the {@link BlobDispatcher} completely delivered a {@link Blob}.
 * <p>
 * This can be triggered by invoking {@link URLBuilder#withHook(String, String)}. The paylod
 * should be short an concise (e.g. a database id). Also note that the payload is by default
 * not protected by any signature or other cryptographic framework as this is mostly used
 * by statistic / logging tasks. If such a protection is required, it has to be enforced
 * manually.
 */
public interface BlobDispatcherHook extends Named {

    /**
     * Processes the given payload after a blob was successfully delivered via the blob dispatcher.
     *
     * @param payload the payload (e.g. a database id to process).
     * @throws Exception in case of a system error when handling the hook.
     */
    void hook(@Nullable String payload) throws Exception;
}
