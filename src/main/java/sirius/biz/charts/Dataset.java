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
 * Represents a dataset for charts and tables. A dateset is understood to consist of a series of {@linkplain Quantity
 * quantities}.
 *
 * @param <N> the numeric type used for the quantities
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

    /**
     * The optional name of the dataset.
     */
    private String name;

    /**
     * An optional primary color to use when displaying the dataset.
     */
    private String color;

    /**
     * The individual quantities of the dataset, indexed by their label.
     */
    private final LinkedHashMap<String, Quantity> quantities = new LinkedHashMap<>();

    /**
     * The cached sum of all quantities, used to avoid renewed computation when invoking {@link #sum()} multiple times.
     *
     * @see #sum()
     */
    private Optional<Double> cachedSum = Optional.empty();

    /**
     * Helper method that allows to format the numeric quantities as needed. Defaults to {@link Object#toString()}.
     */
    private Function<N, String> formatter = Object::toString;

    /**
     * Flag to determine if the rendered charts or tables should also display percentages besides the raw quantities.
     */
    private boolean percentagesShown = false;

    /**
     * Adds a quantity to the dataset.
     *
     * @param label the label of the quantity
     * @param value the value of the quantity
     * @return the dataset itself for fluent method calls
     */
    public Dataset<N> addQuantity(String label, N value) {
        if (Strings.isEmpty(label)) {
            throw new IllegalArgumentException("Label must not be empty.");
        }
        if (value.doubleValue() < 0.0) {
            throw new IllegalArgumentException("Quantity must be non-negative.");
        }

        quantities.compute(label, (key, quantity) -> {
            if (quantity == null) {
                return new Quantity(quantities.size(), label, value);
            }
            quantity.setValue(value);
            return quantity;
        });
        cachedSum = Optional.empty();

        return this;
    }

    /**
     * Retrieves a quantity by its label.
     *
     * @param label the label of the quantity to retrieve
     * @return the quantity with the given label, or an empty optional if no such quantity exists
     */
    public Optional<Quantity> resolveQuantity(String label) {
        return Optional.ofNullable(quantities.get(label));
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
     * Enables the display of percentages for each quantity.
     *
     * @return the dataset itself for fluent method calls
     */
    public Dataset<N> withPercentagesShown() {
        this.percentagesShown = true;
        return this;
    }

    /**
     * Enables the <em>additional</em> display of percentages for each quantity.
     *
     * @param percentagesShown determines if the percentages should be displayed
     * @return the dataset itself for fluent method calls
     */
    public Dataset<N> withPercentagesShown(boolean percentagesShown) {
        this.percentagesShown = percentagesShown;
        return this;
    }

    /**
     * Sets a formatter for the numeric values of the quantities.
     *
     * @param formatter the formatter to use
     * @return the dataset itself for fluent method calls
     */
    public Dataset<N> withFormatter(Function<N, String> formatter) {
        this.formatter = formatter;
        return this;
    }

    /**
     * Computes a new dataset containing the percentages of all quantities of this dataset.
     *
     * @return a new dataset containing the percentages of all quantities
     */
    public Dataset<Double> computePercentageDataset() {
        var result = new Dataset<Double>().withFormatter(number -> String.format("%.2f %%", number));
        for (Quantity quantity : quantities.values()) {
            result.addQuantity(quantity.getLabel(), quantity.percentageValue());
        }
        return result;
    }

    /**
     * Determines the sum of all quantities.
     *
     * @return the sum of all quantities
     */
    public double sum() {
        if (cachedSum.isEmpty()) {
            cachedSum = Optional.of(quantities.values()
                                              .stream()
                                              .map(Quantity::getValue)
                                              .mapToDouble(Number::doubleValue)
                                              .sum());
        }
        return cachedSum.get();
    }

    /**
     * Streams all quantities of the dataset.
     *
     * @return a stream of all quantities
     */
    public Stream<Quantity> stream() {
        return quantities.values().stream();
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
        return quantities.sequencedKeySet();
    }

    public List<Quantity> getQuantities() {
        return List.copyOf(quantities.values());
    }

    /**
     * Represents a single quantity of the dataset. Effectively, a quantity has a numeric value and additional metadata.
     */
    public class Quantity {
        private final int index;
        private final String label;
        private N value;
        private final String color;

        private Quantity(int index, String label, N value) {
            this.index = index;
            this.label = label;
            this.value = value;
            this.color = COLORS.get(index % COLORS.size());
        }

        /**
         * Computes the equivalent double-precision value of this quantity.
         *
         * @return the quantity's value as a double-precision number
         */
        public double doubleValue() {
            return value.doubleValue();
        }

        /**
         * Computes the percentage of this quantity in comparison to the sum of all quantities.
         *
         * @return the percentage of this quantity in comparison to the sum of all quantities as a double-precision number
         */
        public double percentageValue() {
            // the "+ 1.0e-20" is to avoid division by zero; the value is small enough to not affect the result
            return 100 * value.doubleValue() / (sum() + 1.0e-20);
        }

        /**
         * Formats the value of this quantity as a string.
         *
         * @return the formatted value of this quantity
         * @see #withFormatter(Function)
         */
        public String formatValue() {
            return formatter.apply(value);
        }

        /**
         * Formats the percentage of this quantity as a string.
         *
         * @return the formatted percentage of this quantity
         * @see #percentageValue()
         */
        public String formatPercentage() {
            if (!percentagesShown) {
                return "";
            }
            return String.format("%.2f %%", percentageValue());
        }

        /**
         * Formats the quantity as a string, potentially also including a percentage.
         *
         * @return the formatted quantity
         */
        public String formatQuantity() {
            if (!percentagesShown) {
                return formatValue();
            }
            return new StringBuilder(formatValue()).append(String.format(" (%s)", formatPercentage())).toString();
        }

        /**
         * Fetches the equivalent quantity from a previous dataset.
         *
         * @param previousDataset the previous dataset to fetch the quantity from
         * @return the equivalent quantity from the previous dataset, or an empty optional if no such quantity exists
         */
        public Optional<Quantity> fetchPreviousQuantity(Dataset<N> previousDataset) {
            return previousDataset.resolveQuantity(label);
        }

        /**
         * Fetches the value of the equivalent quantity from a previous dataset.
         *
         * @param previousDataset the previous dataset to fetch the quantity from
         * @return the value of the equivalent quantity from the previous dataset, or an empty optional if no such quantity exists
         */
        public Optional<N> fetchPreviousValue(Dataset<N> previousDataset) {
            return fetchPreviousQuantity(previousDataset).map(Quantity::getValue);
        }

        public int getIndex() {
            return index;
        }

        public String getLabel() {
            return label;
        }

        public N getValue() {
            return value;
        }

        private void setValue(N value) {
            this.value = value;
        }

        public String getColor() {
            return color;
        }
    }
}
