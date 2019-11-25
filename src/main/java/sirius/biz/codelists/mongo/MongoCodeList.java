/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists.mongo;

import sirius.biz.codelists.CodeList;
import sirius.biz.codelists.CodeListData;
import sirius.biz.tenants.mongo.MongoTenantAware;
import sirius.db.mixing.annotations.Index;
import sirius.db.mixing.annotations.TranslationSource;
import sirius.db.mongo.Mango;
import sirius.kernel.di.std.Framework;

/**
 * Provides the MongoDB implementation of {@link CodeList}.
 */
@Framework(MongoCodeLists.FRAMEWORK_CODE_LISTS_MONGO)
@TranslationSource(CodeList.class)
@Index(name = "lookup",
        columns = {"tenant", "codeListData_code"},
        columnSettings = {Mango.INDEX_ASCENDING, Mango.INDEX_ASCENDING},
        unique = true)
public class MongoCodeList extends MongoTenantAware implements CodeList {

    private final CodeListData codeListData = new CodeListData(this);

    @Override
    public CodeListData getCodeListData() {
        return codeListData;
    }

    @Override
    public String toString() {
        return codeListData.getCode();
    }
}
