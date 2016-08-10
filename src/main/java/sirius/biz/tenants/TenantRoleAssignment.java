/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.db.mixing.Column;
import sirius.db.mixing.Entity;
import sirius.db.mixing.EntityRef;

/**
 * Created by aha on 10.08.16.
 */
public class TenantRoleAssignment extends Entity {

    public static final Column ROLE = Column.named("role");
    private final EntityRef<TenantRole> role = EntityRef.on(TenantRole.class, EntityRef.OnDelete.CASCADE);

    public static final Column RELATION = Column.named("relation");
    private final EntityRef<TenantRelation> relation = EntityRef.on(TenantRelation.class, EntityRef.OnDelete.CASCADE);

    public EntityRef<TenantRole> getRole() {
        return role;
    }

    public EntityRef<TenantRelation> getRelation() {
        return relation;
    }

}
