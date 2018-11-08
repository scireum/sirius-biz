/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts;

import sirius.kernel.nls.NLS;

public class Dataset {

    private String label;
    private StringBuilder values = new StringBuilder();

    public Dataset(String label) {
        this.label = label;
    }

    public void addValue(Number value) {
        if (values.length() > 0) {
            values.append(", ");
        }
        if (value == null) {
            values.append("null");
        } else {
            values.append(NLS.toMachineString(value));
        }
    }

    public String getLabel() {
        return label;
    }

    public String getData() {
        return values.toString();
    }
}
