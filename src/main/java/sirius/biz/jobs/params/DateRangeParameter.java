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
public class DateRangeParameter extends Parameter<DateRange, DateRangeParameter> {

    private List<DateRange> dateRanges;

    /**
     * Creates a new parameter with the given name, label and any number of {@link DateRange date ranges}
     *
     * @param name       the name of the parameter
     * @param label      the label of the parameter
     * @param dateRanges to fill up the list
     */
    public DateRangeParameter(String name, String label, DateRange... dateRanges) {
        super(name, label);
        this.dateRanges = Arrays.asList(dateRanges);
    }

    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/params/daterange.html.pasta";
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        if (!resolveFromString(input).isPresent()) {
            return null;
        }
        return input.asString();
    }

    @Override
    protected Optional<DateRange> resolveFromString(@Nonnull Value input) {
        if (input.isEmptyString()) {
            return Optional.empty();
        }
        return dateRanges.stream().filter(dateRange -> (input.asString().equals(dateRange.getKey()))).findFirst();
    }

    public List<DateRange> getValues() {
        return Collections.unmodifiableList(dateRanges);
    }
}
