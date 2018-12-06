/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.tenants.SAMLController;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantData;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Controller;

import java.util.List;

/**
 * Permis a login via SAML.
 */
@Register(classes = Controller.class, framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
public class SQLSAMLController extends SAMLController<Long, SQLTenant, SQLUserAccount> {

    @Override
    protected Class<SQLTenant> getTenantClass() {
        return SQLTenant.class;
    }

    @Override
    protected Class<SQLUserAccount> getUserClass() {
        return SQLUserAccount.class;
    }

    @Override
    protected List<SQLTenant> querySAMLTenants() {
        return oma.select(SQLTenant.class)
                  .ne(Tenant.TENANT_DATA.inner(TenantData.SAML_ISSUER_NAME), null)
                  .ne(Tenant.TENANT_DATA.inner(TenantData.SAML_FINGERPRINT), null)
                  .orderAsc(Tenant.TENANT_DATA.inner(TenantData.NAME))
                  .queryList();
    }
}
