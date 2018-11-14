/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts;

import sirius.kernel.nls.NLS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Dataset {

    private String label;
    private List<Number> values = new ArrayList<>();

    public Dataset(String label) {
        this.label = label;
    }

    public Dataset addValue(Number value) {
        values.add(value);
        return this;
    }

    public String getLabel() {
        return label;
    }

    public String getData() {
        return values.stream().map(value -> {
            if (value == null) {
                return "null";
            } else {
                return NLS.toMachineString(value);
            }
        }).collect(Collectors.joining(","));
    }

    public List<Number> getValues() {
        return Collections.unmodifiableList(values);
    }
}
