package sirius.biz.importer;

import sirius.biz.jdbc.tenants.Tenant;

/**
 * Provides a test implementation of a {@link SQLEntityImportHandler} for {@link Tenant tenants}.
 */
public class TenantImportHandler extends SQLEntityImportHandler<Tenant> {
    public TenantImportHandler(ImportContext context) {
        super(Tenant.class, context);
    }
}
