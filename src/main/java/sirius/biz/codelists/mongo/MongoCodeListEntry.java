/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists.mongo;

import sirius.biz.codelists.CodeListEntry;
import sirius.biz.codelists.CodeListEntryData;
import sirius.biz.importer.AutoImport;
import sirius.biz.mongo.PrefixSearchableEntity;
import sirius.db.mixing.annotations.Index;
import sirius.db.mixing.annotations.TranslationSource;
import sirius.db.mongo.Mango;
import sirius.db.mongo.types.MongoRef;
import sirius.kernel.di.std.Framework;
import sirius.kernel.nls.NLS;

/**
 * Provides the MongoDB implementation of {@link CodeListEntry}.
 */
@Framework(MongoCodeLists.FRAMEWORK_CODE_LISTS_MONGO)
@TranslationSource(CodeListEntry.class)
@Index(name = "lookup",
        columns = {"codeList", "codeListEntryData_code"},
        columnSettings = {Mango.INDEX_ASCENDING, Mango.INDEX_ASCENDING},
        unique = true)
public class MongoCodeListEntry extends PrefixSearchableEntity implements CodeListEntry<String, MongoCodeList> {

    @AutoImport
    private final MongoRef<MongoCodeList> codeList =
            MongoRef.writeOnceOn(MongoCodeList.class, MongoRef.OnDelete.CASCADE);
    private final CodeListEntryData codeListEntryData = new CodeListEntryData(this);

    @Override
    public MongoRef<MongoCodeList> getCodeList() {
        return codeList;
    }

    @Override
    public CodeListEntryData getCodeListEntryData() {
        return codeListEntryData;
    }
}
