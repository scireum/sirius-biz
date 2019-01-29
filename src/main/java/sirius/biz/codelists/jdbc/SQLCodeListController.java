/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists.jdbc;

import sirius.biz.codelists.CodeListController;
import sirius.biz.codelists.CodeListData;
import sirius.biz.codelists.CodeListEntryData;
import sirius.biz.web.BasePageHelper;
import sirius.biz.web.SQLPageHelper;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Controller;

@Register(classes = Controller.class, framework = SQLCodeLists.FRAMEWORK_CODE_LISTS_JDBC)
public class SQLCodeListController extends CodeListController<Long, SQLCodeList, SQLCodeListEntry> {

    @Override
    protected BasePageHelper<SQLCodeList, ?, ?, ?> getListsAsPage() {
        return SQLPageHelper.withQuery(tenants.forCurrentTenant(oma.select(SQLCodeList.class)
                                                                   .orderAsc(SQLCodeList.CODE_LIST_DATA.inner(
                                                                           CodeListData.CODE))));
    }

    @Override
    protected BasePageHelper<SQLCodeListEntry, ?, ?, ?> getEntriesAsPage(SQLCodeList codeList) {
        return SQLPageHelper.withQuery(oma.select(SQLCodeListEntry.class)
                                          .eq(SQLCodeListEntry.CODE_LIST, codeList)
                                          .orderAsc(SQLCodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.CODE)));
    }

    @Override
    protected SQLCodeListEntry findOrCreateEntry(SQLCodeList codeList, String code) {
        SQLCodeListEntry cle = oma.select(SQLCodeListEntry.class)
                                  .eq(SQLCodeListEntry.CODE_LIST, codeList)
                                  .eq(SQLCodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.CODE), code)
                                  .queryFirst();
        if (cle == null) {
            cle = new SQLCodeListEntry();
            cle.getCodeList().setValue(codeList);
            cle.getCodeListEntryData().setCode(code);
        }

        return cle;
    }
}
