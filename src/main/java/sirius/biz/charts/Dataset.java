/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.charts;

import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Named;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Represents a dataset for charts and tables.
 *
 * @param <N> the type of the numeric values
 */
public class Dataset<N extends Number> implements Named {

    /**
     * Contains colour hex-strings which can be used for charts.
     */
    public static final List<String> COLORS = List.of("#5eb526", // green
                                                      "#497ebf", // blue
                                                      "#e74c3c", // red
                                                      "#f89406", // orange
                                                      "#f1c40f", // yellow
                                                      "#9b59b6", // purple
                                                      "#1abc9c", // teal
                                                      "#bf4d00", // maroon
                                                      "#9abddf", // light blue
                                                      "#f7b1a3", // light red
                                                      "#b6e2d4" // light teal
    );

    private String name;

    private String color;

    private final LinkedHashMap<String, Slice> slices = new LinkedHashMap<>();

    private Optional<Double> sum = Optional.empty();

    /**
     * Helper that allows to format the numeric values of the slices.
     */
    private Function<N, String> formatter = Object::toString;

    /**
     * Flag to determine if the rendered charts or tables should also display percentages.
     */
    private boolean percentagesShown = false;

    /**
     * Adds a slice to the dataset.
     *
     * @param label    the label of the slice
     * @param quantity the quantity of the slice
     * @return the dataset itself for fluent method calls
     */
    public Dataset<N> addSlice(String label, N quantity) {
        if (Strings.isEmpty(label)) {
            throw new IllegalArgumentException("Label must not be empty.");
        }
        if (quantity.doubleValue() < 0.0) {
            throw new IllegalArgumentException("Quantity must be non-negative.");
        }

        slices.compute(label, (key, slice) -> {
            if (slice == null) {
                return new Slice(slices.size(), label, quantity);
            }
            slice.setQuantity(quantity);
            return slice;
        });
        sum = Optional.empty();

        return this;
    }

    /**
     * Retrieves a slice by its label.
     *
     * @param label the label of the slice
     * @return the slice with the given label or an empty optional if no such slice exists
     */
    public Optional<Slice> resolveSlice(String label) {
        return Optional.ofNullable(slices.get(label));
    }

    /**
     * Assigns a name to the dataset.
     *
     * @param name the name to assign
     * @return the dataset itself for fluent method calls
     */
    public Dataset<N> withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Assigns a color to the dataset.
     *
     * @param color the color to assign
     * @return the dataset itself for fluent method calls
     */
    public Dataset<N> withColor(String color) {
        this.color = color;
        return this;
    }

    /**
     * Enables the display of percentages for each slice.
     *
     * @return the dataset itself for fluent method calls
     */
    public Dataset<N> withPercentagesShown() {
        this.percentagesShown = true;
        return this;
    }

    /**
     * Enables the display of percentages for each slice.
     *
     * @param percentagesShown determines if the percentages should be displayed
     * @return the dataset itself for fluent method calls
     */
    public Dataset<N> withPercentagesShown(boolean percentagesShown) {
        this.percentagesShown = percentagesShown;
        return this;
    }

    /**
     * Sets a formatter for the numeric values of the slices.
     *
     * @param formatter the formatter to use
     * @return the dataset itself for fluent method calls
     */
    public Dataset<N> withFormatter(Function<N, String> formatter) {
        this.formatter = formatter;
        return this;
    }

    /**
     * Computes a new dataset containing the percentages of all slices of this dataset.
     *
     * @return a new dataset containing the percentages of all slices
     */
    public Dataset<Double> computePercentageDataset() {
        var result = new Dataset<Double>().withFormatter(number -> String.format("%.2f %%", number));
        for (Slice slice : slices.values()) {
            result.addSlice(slice.getLabel(), slice.percentageValue());
        }
        return result;
    }

    /**
     * Determines the sum of all slices.
     *
     * @return the sum of all slices
     */
    public double sum() {
        if (sum.isEmpty()) {
            sum = Optional.of(slices.values().stream().map(Slice::getQuantity).mapToDouble(Number::doubleValue).sum());
        }
        return sum.get();
    }

    /**
     * Streams all slices of the dataset.
     *
     * @return a stream of all slices
     */
    public Stream<Slice> stream() {
        return slices.values().stream();
    }

    @Nonnull
    @Override
    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public SequencedSet<String> getLabels() {
        return slices.sequencedKeySet();
    }

    public List<Slice> getSlices() {
        return List.copyOf(slices.values());
    }

    /**
     * Represents a slice of the datset.
     */
    public class Slice {
        private final int index;
        private final String label;
        private N quantity;
        private final String color;

        private Slice(int index, String label, N quantity) {
            this.index = index;
            this.label = label;
            this.quantity = quantity;
            this.color = COLORS.get(index % COLORS.size());
        }

        /**
         * Converts the quantity to a double value.
         *
         * @return the quantity as a double value
         */
        public double doubleValue() {
            return quantity.doubleValue();
        }

        /**
         * Computes the percentage of this slice.
         *
         * @return the percentage of this slice
         */
        public double percentageValue() {
            // the "+ 1.0e-20" is to avoid division by zero; the value is small enough to not affect the result
            return 100 * quantity.doubleValue() / (sum() + 1.0e-20);
        }

        /**
         * Formats the value of this slice as a string.
         *
         * @return the formatted value of this slice
         */
        public String formatValue() {
            return formatter.apply(quantity);
        }

        /**
         * Formats the percentage of this slice as a string.
         *
         * @return the formatted percentage of this slice
         */
        public String formatPercentage() {
            if (!percentagesShown) {
                return "";
            }
            return String.format("%.2f %%", percentageValue());
        }

        /**
         * Formats the quantity as a string, potentially including a percentage.
         *
         * @return the formatted quantity
         */
        public String formatQuantity() {
            if (!percentagesShown) {
                return formatValue();
            }
            return new StringBuilder(quantity.toString()).append(String.format(" (%s)", formatPercentage())).toString();
        }

        /**
         * Fetches the equivalent slice from a previous dataset.
         *
         * @param previousDataset the previous dataset to fetch the slice from
         * @return the equivalent slice from the previous dataset, or an empty optional if no such slice exists
         */
        public Optional<Slice> fetchPreviousSlice(Dataset<N> previousDataset) {
            return previousDataset.resolveSlice(label);
        }

        /**
         * Fetches the quantity of the equivalent slice from a previous dataset.
         *
         * @param previousDataset the previous dataset to fetch the quantity from
         * @return the quantity of the equivalent slice from the previous dataset, or an empty optional if no such slice exists
         */
        public Optional<N> fetchPreviousQuantity(Dataset<N> previousDataset) {
            return fetchPreviousSlice(previousDataset).map(Slice::getQuantity);
        }

        public int getIndex() {
            return index;
        }

        public String getLabel() {
            return label;
        }

        public N getQuantity() {
            return quantity;
        }

        private void setQuantity(N quantity) {
            this.quantity = quantity;
        }

        public String getColor() {
            return color;
        }
    }
}
