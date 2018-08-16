package sirius.biz.importer;

import sirius.biz.jdbc.tenants.Tenant;
import sirius.kernel.di.std.Register;

/**
 * Provides a test implementation of a {@link ImportHandlerFactory} for {@link Tenant tenants}.
 */
@Register
public class TenantImportHandlerFactory implements ImportHandlerFactory {
    @Override
    public boolean accepts(Class<?> type) {
        return type == Tenant.class;
    }

    @Override
    public ImportHandler<?> create(Class<?> type, ImportContext context) {
        return new TenantImportHandler(context);
    }
}
