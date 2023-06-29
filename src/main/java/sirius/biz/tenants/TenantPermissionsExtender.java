/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.importer.BaseImportHandler;
import sirius.biz.importer.EntityImportHandlerExtender;
import sirius.biz.importer.ImporterContext;
import sirius.biz.importer.format.FieldDefinition;
import sirius.biz.tenants.jdbc.SQLTenantImportHandler;
import sirius.biz.tenants.mongo.MongoTenantImportHandler;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mapping;
import sirius.kernel.commons.Explain;
import sirius.kernel.di.std.Register;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Adds the computed <tt>permissions</tt> field to the {@link MongoTenantImportHandler} and {@link SQLTenantImportHandler}.
 */
@Register
public class TenantPermissionsExtender implements EntityImportHandlerExtender {

    private static final Mapping PERMISSIONS = Mapping.named("permissions");

    @Override
    public FieldDefinition resolveCustomField(BaseImportHandler<? extends BaseEntity<?>> handler,
                                              EntityDescriptor descriptor,
                                              String field) {
        if (checkTenantImportHandler(handler) && PERMISSIONS.getName().equals(field)) {
            return FieldDefinition.stringField(field).withLabel("$Tenant.permissions").addAlias("permissions");
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    @Explain("We know that the extractor is only called for the correct type.")
    public <E extends BaseEntity<?>> Function<? super E, ?> createExtractor(BaseImportHandler<E> handler,
                                                                            EntityDescriptor descriptor,
                                                                            ImporterContext context,
                                                                            String fieldToExport) {
        if (checkTenantImportHandler(handler) && PERMISSIONS.getName().equals(fieldToExport)) {
            return tenant -> String.join(",", tenant.as(Tenant.class).getPermissions());
        }
        return null;
    }

    @Override
    public void collectDefaultExportableMappings(BaseImportHandler<? extends BaseEntity<?>> handler,
                                                 EntityDescriptor descriptor,
                                                 BiConsumer<Integer, Mapping> collector) {
        if (checkTenantImportHandler(handler)) {
            collector.accept(185, PERMISSIONS);
        }
    }

    private boolean checkTenantImportHandler(BaseImportHandler<? extends BaseEntity<?>> handler) {
        return handler instanceof MongoTenantImportHandler || handler instanceof SQLTenantImportHandler;
    }
}
