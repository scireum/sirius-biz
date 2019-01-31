/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists.jdbc;

import sirius.biz.codelists.CodeList;
import sirius.biz.codelists.CodeListData;
import sirius.biz.tenants.jdbc.SQLTenantAware;
import sirius.kernel.di.std.Framework;

/**
 * Provides the JDBC/SQL implementation of {@link CodeList}.
 */
@Framework(SQLCodeLists.FRAMEWORK_CODE_LISTS_JDBC)
public class SQLCodeList extends SQLTenantAware implements CodeList {

    private final CodeListData codeListData = new CodeListData(this);

    @Override
    public CodeListData getCodeListData() {
        return codeListData;
    }
}
