/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.analytics.flags.PerformanceDataImportExtender;
import sirius.biz.importer.ImportHandler;
import sirius.biz.importer.ImportHandlerFactory;
import sirius.biz.importer.ImporterContext;
import sirius.biz.importer.SQLEntityImportHandler;
import sirius.biz.model.AddressData;
import sirius.biz.packages.PackageData;
import sirius.biz.tenants.TenantData;
import sirius.db.jdbc.batch.FindQuery;
import sirius.db.mixing.Mapping;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;

import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Provides an import handler for {@link SQLTenant tenants}.
 */
public class SQLTenantImportHandler extends SQLEntityImportHandler<SQLTenant> {

    /**
     * Provides the factory to instantiate this import handler.
     */
    @Register(framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
    public static class SQLTenantImportHandlerFactory implements ImportHandlerFactory {

        @Override
        public boolean accepts(Class<?> type, ImporterContext context) {
            return type == SQLTenant.class;
        }

        @Override
        public ImportHandler<?> create(Class<?> type, ImporterContext context) {
            return new SQLTenantImportHandler(type, context);
        }
    }

    /**
     * Creates a new instance for the given type of entities and import context.
     *
     * @param clazz   the type of entities being handled
     * @param context the import context to use
     */
    protected SQLTenantImportHandler(Class<?> clazz, ImporterContext context) {
        super(clazz, context);
    }

    @Override
    protected void collectFindQueries(BiConsumer<Predicate<SQLTenant>, Supplier<FindQuery<SQLTenant>>> queryConsumer) {
        super.collectFindQueries(queryConsumer);
        queryConsumer.accept(tenant -> Strings.isFilled(tenant.getTenantData().getAccountNumber()),
                             () -> context.getBatchContext()
                                          .findQuery(SQLTenant.class,
                                                     SQLTenant.TENANT_DATA.inner(TenantData.ACCOUNT_NUMBER)));
    }

    @Override
    protected void collectDefaultExportableMappings(BiConsumer<Integer, Mapping> collector) {
        collector.accept(100, SQLTenant.TENANT_DATA.inner(TenantData.ACCOUNT_NUMBER));
        collector.accept(110, SQLTenant.TENANT_DATA.inner(TenantData.NAME));
        collector.accept(120, SQLTenant.TENANT_DATA.inner(TenantData.FULL_NAME));
        collector.accept(130, SQLTenant.TENANT_DATA.inner(TenantData.LANG));
        collector.accept(140, SQLTenant.TENANT_DATA.inner(TenantData.ADDRESS).inner(AddressData.STREET));
        collector.accept(150, SQLTenant.TENANT_DATA.inner(TenantData.ADDRESS).inner(AddressData.ZIP));
        collector.accept(160, SQLTenant.TENANT_DATA.inner(TenantData.ADDRESS).inner(AddressData.CITY));
        collector.accept(170, SQLTenant.TENANT_DATA.inner(TenantData.PACKAGE_DATA).inner(PackageData.PACKAGE_STRING));
        collector.accept(180, SQLTenant.TENANT_DATA.inner(TenantData.PACKAGE_DATA).inner(PackageData.UPGRADES));
        collector.accept(190, PerformanceDataImportExtender.PERFORMANCE_FLAGS);
    }
}
