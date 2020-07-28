/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.biz.tenants.Tenant;
import sirius.biz.web.TenantAware;
import sirius.db.mixing.Mapping;
import sirius.kernel.commons.Explain;

/**
 * Provides the database independent interface for describing a code list.
 * <p>
 * Note that all fields are represented via {@link CodeListData}.
 */
@SuppressWarnings("squid:S1214")
@Explain("We rather keep the constants here, as this emulates the behaviour and layout of a real entity.")
public interface CodeList extends TenantAware {

    /**
     * Contains the effective fields which are mapped by the appropriate mapper depending on the actual entity type.
     */
    Mapping CODE_LIST_DATA = Mapping.named("codeListData");

    CodeListData getCodeListData();

    /**
     * Fills the tenant with the given one.
     *
     * @param tenant the tenant to set for this entity
     */
    void withTenant(Tenant<?> tenant);
}
