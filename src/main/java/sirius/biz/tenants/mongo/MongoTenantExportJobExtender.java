/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.tenants.TenantExportJobExtender;
import sirius.db.mongo.MongoQuery;
import sirius.kernel.di.std.AutoRegister;

/**
 * Provides a specialised interface for extending job factories which export {@linkplain MongoTenant tenants}.
 */
@AutoRegister
public interface MongoTenantExportJobExtender extends TenantExportJobExtender<MongoTenant, MongoQuery<MongoTenant>> {
}
