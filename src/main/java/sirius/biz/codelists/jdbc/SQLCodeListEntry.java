/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists.jdbc;

import sirius.biz.codelists.CodeListEntry;
import sirius.biz.codelists.CodeListEntryData;
import sirius.db.jdbc.SQLEntity;
import sirius.db.jdbc.SQLEntityRef;
import sirius.kernel.di.std.Framework;

/**
 * Provides the JDBC/SQL implementation of {@link CodeListEntry}.
 */
@Framework(SQLCodeLists.FRAMEWORK_CODE_LISTS_JDBC)
public class SQLCodeListEntry extends SQLEntity implements CodeListEntry<Long, SQLCodeList> {

    private final SQLEntityRef<SQLCodeList> codeList =
            SQLEntityRef.on(SQLCodeList.class, SQLEntityRef.OnDelete.CASCADE);

    private final CodeListEntryData codeListEntryData = new CodeListEntryData(this);

    @Override
    public SQLEntityRef<SQLCodeList> getCodeList() {
        return codeList;
    }

    @Override
    public CodeListEntryData getCodeListEntryData() {
        return codeListEntryData;
    }
}
