/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.tenants.TenantExportJobExtender;
import sirius.db.jdbc.SmartQuery;
import sirius.kernel.di.std.AutoRegister;

/**
 * Provides a specialised interface for extending job factories which export {@linkplain SQLTenant tenants}.
 */
@AutoRegister
public interface SQLTenantExportJobExtender extends TenantExportJobExtender<SQLTenant, SmartQuery<SQLTenant>> {
}
