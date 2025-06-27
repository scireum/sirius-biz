/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.db.mixing.Nested;
import sirius.kernel.commons.Strings;
import sirius.kernel.nls.NLS;

/**
 * Represents a link which has been stored for a process.
 */
public class ProcessLink extends Nested {

    private String label;

    private String uri;

    /**
     * Specifies the label used for the link
     *
     * @param label the label of the link which will be {@link NLS#smartGet(String) auto translated}
     * @return the link itself for fluent method calls
     */
    public ProcessLink withLabel(String label) {
        this.label = label;
        return this;
    }

    /**
     * Specifies the URI to link to.
     *
     * @param uri the uri to link to
     * @return the link itself for fluent method calls
     */
    public ProcessLink withUri(String uri) {
        this.uri = uri;
        return this;
    }

    public String getLabel() {
        return NLS.smartGet(label);
    }

    public String getUri() {
        return uri;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof ProcessLink otherLink) {
            return Strings.areEqual(label, otherLink.label) && Strings.areEqual(uri, otherLink.uri);
        }

        return false;
    }

    @Override
    public int hashCode() {
        int labelHash = label != null ? label.hashCode() : "".hashCode();
        int uriHash = uri != null ? uri.hashCode() : "".hashCode();
        return labelHash ^ uriHash;
    }
}
