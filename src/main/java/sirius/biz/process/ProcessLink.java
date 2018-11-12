/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.db.mixing.Nested;

public class ProcessLink extends Nested {

    private String label;

    private String uri;

    public ProcessLink withLabel(String label) {
        this.label = label;
        return this;
    }

    public String getLabel() {
        return label;
    }

    public ProcessLink withUri(String uri) {
        this.uri = uri;
        return this;
    }

    public String getUri() {
        return uri;
    }
}
