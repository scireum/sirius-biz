/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jdbc;

import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Helps in displaying database contents in the UI and in export files.
 */
@Register(classes = DatabaseDisplayUtils.class)
public class DatabaseDisplayUtils {

    /**
     * Formats the given value from the database for display.
     *
     * @param value the value to format
     * @return the formatted value
     */
    public String formatValueForDisplay(@Nullable Object value) {
        if (value == null) {
            return "";
        }

        if (value.getClass().isArray()) {
            return Arrays.stream((Object[]) value).map(NLS::toUserString).collect(Collectors.joining(", "));
        }

        if (value instanceof Array sqlArrayValue) {
            try {
                return Arrays.stream((Object[]) sqlArrayValue.getArray())
                             .map(NLS::toUserString)
                             .collect(Collectors.joining(", "));
            } catch (SQLException exception) {
                Exceptions.ignore(exception);
            }
        }

        return NLS.toUserString(value);
    }
}
