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

    /**
     * Contains the id of the right Y axis.
     */
    public static final String AXIS_RIGHT = "right";

    private String axis;
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
     * Specifies which axis to use.
     *
     * @param axisId the axis to use. Most probably this will be {@link #AXIS_RIGHT} as left is the default
     * @return the dataset itself for fluent method calls
     */
    public Dataset onAxis(String axisId) {
        this.axis = axisId;
        return this;
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
    public String renderData() {
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

    public String getAxis() {
        return axis;
    }

    /**
     * Returns the axis to use.
     *
     * @return the axis properly encoded to be directly used in JavaScript
     */
    public String renderAxisName() {
        if (axis == null) {
            return "null";
        }

        return "'" + axis + "'";
    }
}
