/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.db.mixing.Nested;
import sirius.db.mixing.annotations.NullAllowed;

public class ProcessLink extends Nested {

    private String label;

    private String uri;

    @NullAllowed
    private String type;

    private boolean output;

    public static ProcessLink outputLink(String label, String uri) {
        return additionalLink(label,uri).asOutput();
    }

    public static ProcessLink additionalLink(String label, String uri) {
        return new ProcessLink().withLabel(label).withUri(uri).asOutput();
    }

    public String getLabel() {
        return label;
    }

    public ProcessLink withLabel(String label) {
        this.label = label;
        return this;
    }

    public String getUri() {
        return uri;
    }

    public ProcessLink withUri(String uri) {
        this.uri = uri;
        return this;
    }

    public String getType() {
        return type;
    }

    public ProcessLink withType(String type) {
        this.type = type;
        return this;
    }

    public boolean isOutput() {
        return output;
    }

    public ProcessLink asOutput() {
        this.output = true;
        return this;
    }
}
