/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists.jdbc;

import sirius.biz.codelists.CodeLists;
import sirius.kernel.di.std.Register;

@Register(classes = {CodeLists.class, SQLCodeLists.class}, framework = SQLCodeLists.FRAMEWORK_CODE_LISTS_JDBC)
public class SQLCodeLists extends CodeLists<Long, SQLCodeList, SQLCodeListEntry> {

    /**
     * Names the framework which must be enabled to activate the code lists feature.
     */
    public static final String FRAMEWORK_CODE_LISTS_JDBC = "biz.code-lists-jdbc";

    @Override
    protected Class<SQLCodeListEntry> getEntryType() {
        return SQLCodeListEntry.class;
    }

    @Override
    protected Class<SQLCodeList> getListType() {
        return SQLCodeList.class;
    }
}
