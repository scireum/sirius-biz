/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.db.mixing.DateRange;
import sirius.kernel.commons.Value;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Defines an extension of {@link EnumParameter} for {@link DateRange}.
 */
public class DateRangeParameter extends ParameterBuilder<DateRange, DateRangeParameter> {

    private final List<DateRange> dateRanges;
    private DateRange defaultValue;

    /**
     * Creates a new parameter with the given name, label and any number of {@link DateRange date ranges}
     *
     * @param name       the name of the parameter
     * @param label      the label of the parameter
     * @param dateRanges the list of ranges to offer
     */
    public DateRangeParameter(String name, String label, DateRange... dateRanges) {
        super(name, label);
        this.dateRanges = Arrays.asList(dateRanges);
    }

    /**
     * Creates a new parameter with the given name, label and a default list of {@link DateRange date ranges}
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter
     */
    public DateRangeParameter(String name, String label) {
        super(name, label);
        this.dateRanges = Arrays.asList(DateRange.TODAY,
                                        DateRange.YESTERDAY,
                                        DateRange.THIS_WEEK,
                                        DateRange.LAST_WEEK,
                                        DateRange.THIS_MONTH,
                                        DateRange.LAST_MONTH,
                                        DateRange.THIS_YEAR,
                                        DateRange.LAST_YEAR);
    }

    /**
     * Creates a new parameter with the given name and default label and the list of {@link DateRange date ranges}
     *
     * @param name       the name of the parameter
     * @param dateRanges the list of ranges to offer
     */
    public DateRangeParameter(String name, DateRange... dateRanges) {
        this(name, "$DateRangeParameter.label", dateRanges);
    }

    /**
     * Creates a new parameter with the default name, label and the list of {@link DateRange date ranges}
     *
     * @param dateRanges the list of ranges to offer
     */
    public DateRangeParameter(DateRange... dateRanges) {
        this("dateRange", dateRanges);
    }

    /**
     * Specifies the default value to use.
     *
     * @param defaultValue the default value to use
     * @return the parameter itself for fluent method calls
     */
    public DateRangeParameter withDefault(DateRange defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/params/daterange.html.pasta";
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        if (input.isNull()) {
            return defaultValue != null ? defaultValue.getKey() : null;
        }

        if (resolveFromString(input).isEmpty()) {
            return null;
        }
        return input.asString();
    }

    @Override
    protected Optional<DateRange> resolveFromString(@Nonnull Value input) {
        if (input.isEmptyString()) {
            return Optional.empty();
        }
        return dateRanges.stream().filter(dateRange -> input.asString().equals(dateRange.getKey())).findFirst();
    }

    public List<DateRange> getValues() {
        return Collections.unmodifiableList(dateRanges);
    }
}
