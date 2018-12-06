/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.tenants.ProfileController;
import sirius.biz.tenants.jdbc.SQLTenant;
import sirius.biz.tenants.jdbc.SQLTenants;
import sirius.biz.tenants.jdbc.SQLUserAccount;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Controller;

/**
 * Provides functionality to modify accounts.
 */
@Register(classes = Controller.class, framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
public class SQLProfileController extends ProfileController<Long, SQLTenant, SQLUserAccount> {

    @Override
    protected Class<SQLUserAccount> getUserClass() {
        return SQLUserAccount.class;
    }

}

