/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.model.BizEntity;
import sirius.biz.web.Autoloaded;
import sirius.db.mixing.Column;
import sirius.db.mixing.EntityRef;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;

/**
 * Created by aha on 10.08.16.
 */
public class TenantRelation extends BizEntity {

    public static final Column FROM_TENANT = Column.named("fromTenant");
    private final EntityRef<Tenant> fromTenant = EntityRef.on(Tenant.class, EntityRef.OnDelete.CASCADE);

    public static final Column TO_TENANT = Column.named("toTenant");
    private final EntityRef<Tenant> toTenant = EntityRef.on(Tenant.class, EntityRef.OnDelete.CASCADE);

    public static final Column SUPPORT_ENABLED = Column.named("supportEnabled");
    private boolean supportEnabled;

    /**
     * Contains all permissions as a single string, separated with commas.
     */
    public static final Column PERMISSION_STRING = Column.named("permissionString");
    @Autoloaded
    @NullAllowed
    @Length(4096)
    private String permissionString;

    public EntityRef<Tenant> getFromTenant() {
        return fromTenant;
    }

    public EntityRef<Tenant> getToTenant() {
        return toTenant;
    }
}
