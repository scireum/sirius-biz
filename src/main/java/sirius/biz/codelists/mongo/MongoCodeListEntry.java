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
import sirius.db.mixing.annotations.TranslationSource;
import sirius.db.mongo.MongoEntity;
import sirius.db.mongo.types.MongoRef;
import sirius.kernel.di.std.Framework;

/**
 * Provides the MongoDB implementation of {@link CodeListEntry}.
 */
@Framework(MongoCodeLists.FRAMEWORK_CODE_LISTS_MONGO)
@TranslationSource(CodeListEntry.class)
public class MongoCodeListEntry extends MongoEntity implements CodeListEntry<String, MongoCodeList> {

    private final MongoRef<MongoCodeList> codeList = MongoRef.on(MongoCodeList.class, MongoRef.OnDelete.CASCADE);
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
