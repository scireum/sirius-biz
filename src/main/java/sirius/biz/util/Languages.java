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
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

/**
 * Provides a helper class to query the <tt>countries</tt> {@link LookupTable}.
 * <p>
 * Note that the table can be backed by either a {@link sirius.biz.codelists.CodeLists code list} or a
 * {@link sirius.biz.jupiter.Jupiter IDB table}. For most of the additional fields to work properly,
 * the Jupiter binding must be active.
 */
@Register(classes = Languages.class)
public class Languages {

    /**
     * Contains the name of the main lookup table which contains all known languages.
     */
    public static final String LOOKUP_TABLE_ALL_LANGUAGES = "languages";

    /**
     * Contains the name of the lookup table which contains all languages which are "active" / "shown to the user".
     * <p>
     * This can be a subset of <tt>LOOKUP_TABLE_ALL_LANGUAGES</tt> in case only a limited set of languages should
     * be provided in select lists etc. Note that applications can also define their own sub sets for custom purposes
     * and still use the helper methods provided here.
     */
    public static final String LOOKUP_TABLE_ACTIVE_LANGUAGES = "active-languages";

    /**
     * Contains the ISO 639-1 code to be used with {@link LookupTable#fetchMapping(String, String)} etc.
     */
    public static final String MAPPING_ISO_639_1 = "iso639-1";

    /**
     * Contains the ISO 639-2B code to be used with {@link LookupTable#fetchMapping(String, String)} etc.
     */
    public static final String MAPPING_ISO_639_2B = "iso639-2B";

    /**
     * Contains the ISO 639-2T code to be used with {@link LookupTable#fetchMapping(String, String)} etc.
     */
    public static final String MAPPING_ISO_639_2T = "iso639-2T";

    /**
     * Provides the name of a mapping used to migrate legacy language codes
     */
    public static final String MAPPING_LEGACY = "legacy";

    @Part
    private LookupTables lookupTables;

    /**
     * Provides access to the underlying lookup table for all languages
     *
     * @return the lookup table for all languages
     */
    public LookupTable all() {
        return lookupTables.fetchTable(LOOKUP_TABLE_ALL_LANGUAGES);
    }

    /**
     * Provides access to the underlying lookup table for all active languages.
     *
     * @return the lookup table for active languages
     */
    public LookupTable active() {
        return lookupTables.fetchTable(LOOKUP_TABLE_ACTIVE_LANGUAGES);
    }
}
