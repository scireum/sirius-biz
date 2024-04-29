/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists.jdbc;

import sirius.biz.codelists.CodeList;
import sirius.biz.codelists.CodeListController;
import sirius.biz.codelists.CodeListData;
import sirius.biz.codelists.CodeListEntry;
import sirius.biz.codelists.CodeListEntryData;
import sirius.biz.web.BasePageHelper;
import sirius.biz.web.SQLPageHelper;
import sirius.db.mixing.query.QueryField;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;

/**
 * Provides the JDBC/SQL implementation of the {@link CodeListController}.
 */
@Register(framework = SQLCodeLists.FRAMEWORK_CODE_LISTS_JDBC)
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
                                          .orderAsc(SQLCodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.PRIORITY))
                                          .orderAsc(SQLCodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.CODE)));
    }

    @Override
    protected void applyCodeListSearchFields(@Nonnull BasePageHelper<SQLCodeList, ?, ?, ?> pageHelper) {
        pageHelper.withSearchFields(QueryField.contains(CodeList.CODE_LIST_DATA.inner(CodeListData.CODE)),
                                    QueryField.contains(CodeList.CODE_LIST_DATA.inner(CodeListData.NAME)),
                                    QueryField.contains(CodeList.CODE_LIST_DATA.inner(CodeListData.DESCRIPTION)));
    }

    @Override
    protected void applyCodeListEntrySearchFields(@Nonnull BasePageHelper<SQLCodeListEntry, ?, ?, ?> pageHelper) {
        pageHelper.withSearchFields(QueryField.contains(CodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.CODE)),
                                    QueryField.contains(CodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.VALUE)),
                                    QueryField.contains(CodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.ADDITIONAL_VALUE)),
                                    QueryField.contains(CodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.DESCRIPTION)));
    }
}
