/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.db.mixing.Nested;

public class ProcessOutputTable extends Nested {

    private String name;

    private String label;

    private String type;

    public ProcessOutputTable withName(String name) {
        this.name = name;
        return this;
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public ProcessOutputTable withLabel(String label) {
        this.label = label;
        return this;
    }

    public String getType() {
        return type;
    }

    public ProcessOutputTable withType(String type) {
        this.type = type;
        return this;
    }
}
