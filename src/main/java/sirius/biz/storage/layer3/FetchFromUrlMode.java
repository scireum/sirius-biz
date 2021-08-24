/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import java.net.URL;
import java.util.function.Predicate;

/**
 * Determines the behavior of {@link VirtualFile#loadFromUrl(URL, FetchFromUrlMode)} or
 * {@link VirtualFile#resolveOrLoadChildFromURL(URL, FetchFromUrlMode, Predicate)}.
 */
public enum FetchFromUrlMode {
    /**
     * Fetches the data from the URL if either the local file does not exist or if the contents on the remote server
     * have been modified since the last fetch.
     */
    NON_EXISTENT_OR_MODIFIED,

    /**
     * Only fetches the data from the server if the local file does not exist.
     */
    NON_EXISTENT,

    /**
     * Always fetches the data from the server.
     */
    ALWAYS_FETCH,

    /**
     * Never fetches any data from the server. Note that this will not perform any network request and thus for URLs
     * which do not reveal the effective file name, <tt>VirtualFile.resolveOrLoadChildFromURL</tt> will fail with an
     * appropriate error message.
     */
    NEVER_FETCH
}
