/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.analytics.flags.PerformanceDataImportExtender;
import sirius.biz.importer.ImportHandler;
import sirius.biz.importer.ImportHandlerFactory;
import sirius.biz.importer.ImporterContext;
import sirius.biz.importer.MongoEntityImportHandler;
import sirius.biz.model.AddressData;
import sirius.biz.model.LoginData;
import sirius.biz.model.PermissionData;
import sirius.biz.model.PersonData;
import sirius.biz.packages.PackageData;
import sirius.biz.tenants.TenantData;
import sirius.biz.tenants.UserAccountData;
import sirius.db.mixing.Mapping;
import sirius.kernel.commons.Context;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Provides an import handler for {@link MongoTenant tenants}.
 */
public class MongoTenantImportHandler extends MongoEntityImportHandler<MongoTenant> {

    @Part
    protected MongoTenants tenants;

    /**
     * Provides the factory to instantiate this import handler.
     */
    @Register(framework = MongoTenants.FRAMEWORK_TENANTS_MONGO)
    public static class MongoTenantImportHandlerFactory implements ImportHandlerFactory {

        @Override
        public boolean accepts(Class<?> type, ImporterContext context) {
            return type == MongoTenant.class;
        }

        @Override
        public ImportHandler<?> create(Class<?> type, ImporterContext context) {
            return new MongoTenantImportHandler(type, context);
        }
    }

    /**
     * Creates a new instance for the given type of entities and import context.
     *
     * @param clazz   the type of entities being handled
     * @param context the import context to use
     */
    protected MongoTenantImportHandler(Class<?> clazz, ImporterContext context) {
        super(clazz, context);
    }

    @Override
    public Optional<MongoTenant> tryFind(Context data) {
        if (data.containsKey(MongoTenant.ID.getName())) {
            return mango.select(MongoTenant.class)
                        .eq(MongoTenant.ID, data.getValue(MongoTenant.ID.getName()).asString())
                        .one();
        }

        if (data.containsKey(MongoTenant.TENANT_DATA.inner(TenantData.ACCOUNT_NUMBER).toString())) {
            return mango.select(MongoTenant.class)
                        .eq(MongoTenant.TENANT_DATA.inner(TenantData.ACCOUNT_NUMBER),
                            data.getValue(MongoTenant.TENANT_DATA.inner(TenantData.ACCOUNT_NUMBER).toString()))
                        .one();
        }

        return Optional.empty();
    }

    @Override
    protected void collectDefaultExportableMappings(BiConsumer<Integer, Mapping> collector) {
        collector.accept(100, MongoTenant.TENANT_DATA.inner(TenantData.ACCOUNT_NUMBER));
        collector.accept(110, MongoTenant.TENANT_DATA.inner(TenantData.NAME));
        collector.accept(120, MongoTenant.TENANT_DATA.inner(TenantData.FULL_NAME));
        collector.accept(130, MongoTenant.TENANT_DATA.inner(TenantData.LANG));
        collector.accept(140, MongoTenant.TENANT_DATA.inner(TenantData.ADDRESS).inner(AddressData.STREET));
        collector.accept(150, MongoTenant.TENANT_DATA.inner(TenantData.ADDRESS).inner(AddressData.ZIP));
        collector.accept(160, MongoTenant.TENANT_DATA.inner(TenantData.ADDRESS).inner(AddressData.CITY));
        collector.accept(170, MongoTenant.TENANT_DATA.inner(TenantData.PACKAGE_DATA).inner(PackageData.PACKAGE_STRING));
        collector.accept(180, MongoTenant.TENANT_DATA.inner(TenantData.PACKAGE_DATA).inner(PackageData.UPGRADES));
        collector.accept(190, PerformanceDataImportExtender.PERFORMANCE_FLAGS);
    }
}
