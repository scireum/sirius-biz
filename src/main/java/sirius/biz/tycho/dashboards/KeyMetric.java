/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.dashboards;

import sirius.web.services.JSONStructuredOutput;

public class KeyMetric {

    private String label;
    private String value;
    private String description;

    public KeyMetric(String label, String value, String description) {
        this.label = label;
        this.value = value;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public void writeJSON(JSONStructuredOutput output) {
        output.beginObject("metric");
        output.property("type", "KeyMetric");
        output.property("label", label);
        output.property("value", value);
        output.property("description", description);
        output.endObject();
    }
}
