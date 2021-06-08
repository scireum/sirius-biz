/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations;

import sirius.biz.codelists.LookupTables;
import sirius.biz.util.Languages;
import sirius.db.jdbc.SQLEntity;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.kernel.di.std.Part;

/**
 * Represents an entity to test properties of type {@link MultiLanguageString} within JDBC/SQL.
 */
public class SQLMultiLanguageStringEntity extends SQLEntity {
    @Part
    private static LookupTables lookupTables;

    private final MultiLanguageString multiLangTextWithLookupTable =
            new MultiLanguageString().withLookupTable(lookupTables.fetchTable(Languages.LOOKUP_TABLE_ALL_LANGUAGES));

    public static final Mapping MULTILANGTEXT = Mapping.named("multiLangText");
    @NullAllowed
    private final MultiLanguageString multiLangText = new MultiLanguageString();

    public MultiLanguageString getMultiLangText() {
        return multiLangText;
    }

    public MultiLanguageString getMultiLangTextWithLookupTable() {
        return multiLangTextWithLookupTable;
    }
}
