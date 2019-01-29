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
import sirius.biz.codelists.CodeLists;
import sirius.db.jdbc.SQLEntity;
import sirius.db.jdbc.SQLEntityRef;
import sirius.db.mongo.MongoEntity;
import sirius.db.mongo.types.MongoRef;
import sirius.kernel.di.std.Framework;

/**
 * Represents a en entry in a {@link MongoCodeList}.
 * <p>
 * This is the database representation of the data supplied by {@link CodeLists}.
 */
@Framework(MongoCodeLists.FRAMEWORK_CODE_LISTS_MONGO)
public class MongoCodeListEntry extends MongoEntity implements CodeListEntry<String, MongoCodeList> {

    /**
     * References the code list this entrd belongs to.
     */
    private final MongoRef<MongoCodeList> codeList =
            MongoRef.on(MongoCodeList.class, MongoRef.OnDelete.CASCADE);

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
