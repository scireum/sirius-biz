/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists.mongo;

import sirius.biz.codelists.CodeListController;
import sirius.biz.codelists.CodeListData;
import sirius.biz.codelists.CodeListEntryData;
import sirius.biz.web.BasePageHelper;
import sirius.biz.web.MongoPageHelper;
import sirius.kernel.di.std.Register;

/**
 * Provides the MongoDB implementation of the {@link CodeListController}.
 */

@Register(framework = MongoCodeLists.FRAMEWORK_CODE_LISTS_MONGO)
public class MongoCodeListController extends CodeListController<String, MongoCodeList, MongoCodeListEntry> {

    @Override
    protected BasePageHelper<MongoCodeList, ?, ?, ?> getListsAsPage() {
        return MongoPageHelper.withQuery(tenants.forCurrentTenant(mango.select(MongoCodeList.class)
                                                                       .orderAsc(MongoCodeList.CODE_LIST_DATA.inner(
                                                                               CodeListData.CODE))));
    }

    @Override
    protected BasePageHelper<MongoCodeListEntry, ?, ?, ?> getEntriesAsPage(MongoCodeList codeList) {
        return MongoPageHelper.withQuery(mango.select(MongoCodeListEntry.class)
                                              .eq(MongoCodeListEntry.CODE_LIST, codeList)
                                              .orderAsc(MongoCodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.PRIORITY))
                                              .orderAsc(MongoCodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.CODE)));
    }

    @Override
    protected MongoCodeListEntry findOrCreateEntry(MongoCodeList codeList, String code) {
        MongoCodeListEntry cle = mango.select(MongoCodeListEntry.class)
                                      .eq(MongoCodeListEntry.CODE_LIST, codeList)
                                      .eq(MongoCodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.CODE), code)
                                      .queryFirst();
        if (cle == null) {
            cle = new MongoCodeListEntry();
            cle.getCodeList().setValue(codeList);
            cle.getCodeListEntryData().setCode(code);
        }

        return cle;
    }
}
