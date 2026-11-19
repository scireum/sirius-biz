/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.db.mixing.Mapping;
import sirius.db.mongo.MongoEntity;

/**
 * Stores a custom config in MongoDB to test {@link CustomConfigs}.
 */
public class MongoCustomConfigTestEntity extends MongoEntity {

    public static final Mapping PERMISSIONS = Mapping.named("permissions");
    private final PermissionData permissions = new PermissionData(this);

    public PermissionData getPermissions() {
        return permissions;
    }
}
