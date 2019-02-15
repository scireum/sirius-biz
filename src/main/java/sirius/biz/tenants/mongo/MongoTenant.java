/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.mongo.MongoBizEntity;
import sirius.biz.protocol.JournalData;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantData;
import sirius.biz.web.Autoloaded;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mongo.types.MongoRef;
import sirius.kernel.di.std.Framework;

@Framework(MongoTenants.FRAMEWORK_TENANTS_MONGO)
public class MongoTenant extends MongoBizEntity implements Tenant<String> {

    /**
     * Contains the parent tenant of this tenant.
     */
    @Autoloaded
    @NullAllowed
    private final MongoRef<MongoTenant> parent = MongoRef.on(MongoTenant.class, MongoRef.OnDelete.SET_NULL);

    public static final Mapping TENANT_DATA = Mapping.named("tenantData");
    private final TenantData tenantData = new TenantData(this);

    /**
     * Used to record changes on fields of the user.
     */
    public static final Mapping JOURNAL = Mapping.named("journal");
    private final JournalData journal = new JournalData(this);

    @Override
    public void setId(String id) {
        super.setId(id);
    }

    @Override
    public MongoRef<MongoTenant> getParent() {
        return parent;
    }

    @Override
    public TenantData getTenantData() {
        return tenantData;
    }

    @Override
    public JournalData getJournal() {
        return journal;
    }

    @Override
    public String toString() {
        return getTenantData().toString();
    }
}
