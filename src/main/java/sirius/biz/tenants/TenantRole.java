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
import sirius.db.mixing.annotations.Unique;

/**
 * Created by aha on 10.08.16.
 */
public class TenantRole extends BizEntity {

    public static final Column TENANT = Column.named("tenant");
    private final EntityRef<Tenant> tenant = EntityRef.on(Tenant.class, EntityRef.OnDelete.CASCADE);

    public static final Column CODE = Column.named("code");
    @Unique(within = "tenant")
    @Autoloaded
    @Length(50)
    private String code;

    public static final Column NAME = Column.named("name");
    @Autoloaded
    @Length(255)
    private String name;

    public EntityRef<Tenant> getTenant() {
        return tenant;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
