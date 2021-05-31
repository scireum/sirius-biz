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
    public static final String LOOKUP_TABLE_ALL_COUNTRIES = "countries";

    /**
     * Contains the name of the lookup table which contains all countries which are "active" / "shown to the user".
     * <p>
     * This can be a subset of <tt>LOOKUP_TABLE_ALL_COUNTRIES</tt> in case only a limited set of countries should
     * be provided in select lists etc. Note that applications can also define their own sub sets for custom purposes
     * and still use the helper methods provided here.
     */
    public static final String LOOKUP_TABLE_ACTIVE_COUNTRIES = "active-countries";

    /**
     * Contains the two letter ISO country code to be used with {@link LookupTable#fetchMapping(String, String)} etc.
     */
    public static final String MAPPING_ISO2 = "isoAlpha2";

    /**
     * Contains the three letter ISO country code to be used with {@link LookupTable#fetchMapping(String, String)} etc.
     */
    public static final String MAPPING_ISO3 = "isoAlpha3";

    /**
     * Provides the name of a mapping used to migrate legacy language codes
     */
    public static final String MAPPING_LEGACY = "legacy";

    private static final String FIELD_ZIP_REGEX = "zipCodeRegEx";

    @Part
    private LookupTables lookupTables;

    /**
     * Provides access to the underlying lookup table for all countries.
     *
     * @return the lookup table for countries.
     */
    public LookupTable all() {
        return lookupTables.fetchTable(LOOKUP_TABLE_ALL_COUNTRIES);
    }

    /**
     * Provides access to the underlying lookup table for all active countries.
     *
     * @return the lookup table for  active countries.
     */
    public LookupTable active() {
        return lookupTables.fetchTable(LOOKUP_TABLE_ACTIVE_COUNTRIES);
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
            return all().fetchField(code, FIELD_ZIP_REGEX)
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
