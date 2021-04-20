/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.util;

import sirius.biz.codelists.LookupTable;
import sirius.biz.codelists.LookupTables;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nullable;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Provides a helper class to query the <tt>countries</tt> {@link LookupTable}.
 * <p>
 * Note that the table can be backed by either a {@link sirius.biz.codelists.CodeLists code list} or a
 * {@link sirius.biz.jupiter.Jupiter IDB table}. For most of the additional fields to work properly,
 * the Jupiter binding must be active.
 */
@Register(classes = Countries.class)
public class Countries {

    /**
     * Contains the name of the main lookup table which contains all known countries.
     */
    public static final String LOOKUP_TABLE_COUNTRIES = "countries";

    /**
     * Represents the leading code field.
     * <p>
     * By convention we use the two letter country code for this.
     */
    public static final String CODE_FIELD = "code";

    /**
     * Contains the two letter ISO country code.
     */
    public static final String FIELD_ISO2 = "mappings.isoAlpha2";

    /**
     * Contains th three letter ISO country code.
     */
    public static final String FIELD_ISO3 = "mappings.isoAlpha3";

    private static final String FIELD_ZIP_REGEX = "zipCodeRegEx";

    @Part
    private LookupTables lookupTables;

    /**
     * Provides access to the underlying lookup table.
     *
     * @return the lookup table for countries.
     */
    public LookupTable lookupTable() {
        return lookupTables.fetchTable(LOOKUP_TABLE_COUNTRIES);
    }

    /**
     * Determines if the given zip code is valid in the given (normalized) country code.
     *
     * @param code the normalized country code
     * @param zip  the zip code to check
     * @return <tt>true</tt> if the code is valid (or empty), <tt>false</tt> otherwise
     */
    public boolean isValidZipCode(@Nullable String code, @Nullable String zip) {
        if (Strings.isEmpty(zip)) {
            return true;
        }

        try {
            return lookupTable().fetchField(code, FIELD_ZIP_REGEX)
                                .map(Pattern::compile)
                                .map(pattern -> pattern.matcher(zip).matches())
                                .orElse(true);
        } catch (PatternSyntaxException exception) {
            Exceptions.handle()
                      .error(exception)
                      .withSystemErrorMessage("Invalid ZIP regex for country: %s - %s (%s)", code)
                      .handle();
            return true;
        }
    }
}
