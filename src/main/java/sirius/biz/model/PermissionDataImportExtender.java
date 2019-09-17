/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.biz.importer.BaseImportHandler;
import sirius.biz.importer.EntityImportHandlerExtender;
import sirius.biz.importer.ImporterContext;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.Property;
import sirius.kernel.commons.Context;
import sirius.kernel.di.std.Register;

import java.util.function.BiConsumer;

/**
 * Provides an import extender which uses {@link PermissionData#compilePermissionString(String)} for each
 * {@link PermissionData#PERMISSIONS}.
 * <p>
 * Additionally, we provide a way to modify the set of roles: By starting the roles list with a "+"
 * (e.g. <tt>+ROLE,ROLE2</tt>) the roles are just added to the already existing set of permissions. Likewise
 * by starting the roles list with a "-" (e.g. <tt>-ROLE,ROLE2</tt>) the roles are removed.
 */
@Register
public class PermissionDataImportExtender implements EntityImportHandlerExtender {

    @Override
    public void collectLoaders(BaseImportHandler<? extends BaseEntity<?>> handler,
                               EntityDescriptor descriptor,
                               ImporterContext context,
                               BiConsumer<Mapping, BiConsumer<Context, Object>> loaderCollector) {
        for (Property property : descriptor.getProperties()) {
            if (PermissionData.PERMISSIONS.getName().equals(property.getField().getName())
                && property.getField().getDeclaringClass() == PermissionData.class) {
                loaderCollector.accept(Mapping.named(property.getName()),
                                       (data, entity) -> loadPermissions(entity, property, data));
            }
        }
    }

    private void loadPermissions(Object entity, Property property, Context data) {
        PermissionData permissionData = (PermissionData) property.getTarget(entity);
        String permissionString = data.getValue(property.getName()).asString();
        if (permissionString.startsWith("+")) {
            permissionData.getPermissions()
                          .addAll(PermissionData.compilePermissionString(permissionString.substring(1)));
        } else if (permissionString.startsWith("-")) {
            permissionData.getPermissions()
                          .modify()
                          .removeAll(PermissionData.compilePermissionString(permissionString.substring(1)));
        } else {
            permissionData.getPermissions().clear();
            permissionData.getPermissions().addAll(PermissionData.compilePermissionString(permissionString));
        }
    }
}
