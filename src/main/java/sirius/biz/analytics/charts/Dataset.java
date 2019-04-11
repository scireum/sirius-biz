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

/**
 * Defines a dataset which can be passed into a chart.
 * <p>
 * The main purpose is to format the given data into a representation which is usable in the JavaScript implementation
 * which renders the chart.
 */
public class Dataset {

    private String label;
    private List<Number> values = new ArrayList<>();

    /**
     * Creates a new dataset with the given label.
     *
     * @param label a label to use
     */
    public Dataset(String label) {
        this.label = label;
    }

    /**
     * Adds a value to the dataset.
     *
     * @param value the value to add
     * @return the dataset itself for fluent method calls
     */
    public Dataset addValue(Number value) {
        values.add(value);
        return this;
    }

    /**
     * Returns the label of the dataset.
     *
     * @return the label of the dataset
     */
    public String getLabel() {
        return label;
    }

    /**
     * Returns a string representation of the data used by the JavaScript implementation to render a chart.
     *
     * @return a string representation of this dataset
     */
    public String getData() {
        return values.stream().map(value -> {
            if (value == null) {
                return "null";
            } else {
                return NLS.toMachineString(value);
            }
        }).collect(Collectors.joining(","));
    }

    /**
     * Returns the previously collected values within this dataset.
     *
     * @return the values of this dataset
     */
    public List<Number> getValues() {
        return Collections.unmodifiableList(values);
    }
}
