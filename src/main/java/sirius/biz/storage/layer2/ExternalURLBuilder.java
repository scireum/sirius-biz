/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import java.util.Optional;

/**
 * Helper class for {@link BlobSoftRef soft references} which reference an external URL instead of a blob.
 */
class ExternalURLBuilder extends URLBuilder {

    private final String fixedUrl;

    protected ExternalURLBuilder(String fixedUrl) {
        super(null, "");
        this.fixedUrl = fixedUrl;
    }

    @Override
    public Optional<String> buildURL() {
        return Optional.of(fixedUrl);
    }
}
