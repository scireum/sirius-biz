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
 */
public class DateTimeFormatCheck implements ValueCheck {

    private String format;
    private DateTimeFormatter formatter;

    /**
     * Creates a new check using the given date format.
     *
     * @param format the {@link org.joda.time.format.DateTimeFormat} which will be used to check the value
     */
    public DateTimeFormatCheck(@Nonnull String format) {
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
