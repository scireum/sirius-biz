/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.kernel.commons.Value;
import sirius.kernel.nls.NLS;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * Determines if the given values matches a date format.
 * <p>
 * The formatting notation used is the notation described in {@link DateTimeFormatter}.
 * <p>
 * <i>Hint</i>: Use 'u' instead of 'y' for years.
 */
public class DateTimeFormatCheck implements ValueCheck {

    private final String format;
    private final DateTimeFormatter formatter;

    /**
     * Creates a new check using the given date format.
     * <p>
     * The formatting notation used is the notation described in {@link DateTimeFormatter}.
     * <p>
     * <i>Hint</i>: Use 'u' instead of 'y' for years.
     * <p>
     * <i>Reason</i>: We want to use the 'strict' resolver, so dates like 30.02.2019 are marked invalid and not resolved to a
     * valid date. But when using the strict resolver, one needs to give the era (BC or AD) when using 'y' or it does
     * not become a valid date. To avoid this 'u' needs to be used.
     *
     * @param format the String describing the formats of the dates
     * @throws IllegalArgumentException when 'y' is used for year
     */
    public DateTimeFormatCheck(@Nonnull String format) {
        if (format.contains("y")) {
            throw new IllegalArgumentException("Use 'u' instead of 'y' for years in format string.");
        }
        this.format = format;
        this.formatter = DateTimeFormatter.ofPattern(format).withResolverStyle(ResolverStyle.STRICT);
    }

    @Override
    public void perform(Value value) {
        if (value.isEmptyString()) {
            return;
        }

        String stringValue = value.asString();
        try {
            LocalDate.parse(stringValue, formatter);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(NLS.fmtr("DateTimeFormatCheck.errorMsg")
                                                  .set("value", stringValue)
                                                  .set("format", format)
                                                  .format());
        }
    }

    @Nullable
    @Override
    public String generateRemark() {
        return NLS.fmtr("DateTimeFormatCheck.remark").set("format", format).format();
    }
}
