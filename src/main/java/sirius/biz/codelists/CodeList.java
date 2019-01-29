/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.biz.web.TenantAware;
import sirius.db.mixing.Mapping;

public interface CodeList extends TenantAware {

    boolean isNew();

    String getIdAsString();

    Mapping CODE_LIST_DATA = Mapping.named("codeListData");
    CodeListData getCodeListData();

}
