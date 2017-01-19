/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.model.BizEntity;
import sirius.biz.model.InternationalAddressData;
import sirius.biz.model.PermissionData;
import sirius.biz.protocol.JournalData;
import sirius.biz.protocol.Journaled;
import sirius.biz.web.Autoloaded;
import sirius.db.mixing.Column;
import sirius.db.mixing.EntityRef;
import sirius.db.mixing.annotations.BeforeDelete;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Trim;
import sirius.db.mixing.annotations.Unique;
import sirius.db.mixing.annotations.Versioned;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

/**
 * Represents a tenant using the system.
 * <p>
 * Helps to support multi tenancy for SaaS platforms.
 */
@Framework("tenants")
@Versioned
public class Tenant extends BizEntity implements Journaled {

    /**
     * Contains the parent tenant of this tenant.
     */
    public static final Column PARENT = Column.named("parent");
    @Autoloaded
    @NullAllowed
    private final EntityRef<Tenant> parent = EntityRef.on(Tenant.class, EntityRef.OnDelete.SET_NULL);

    /**
     * Determines if the parent tenant can become this tenant by calling "/tenants/select".
     */
    public static final Column PARENT_CAN_ACCESS = Column.named("parentCanAccess");
    @Autoloaded
    private boolean parentCanAccess = false;

    /**
     * Determines if this tenant can become its tenant by calling "/tenants/select".
     */
    public static final Column CAN_ACCESS_PARENT = Column.named("canAccessParent");
    @Autoloaded
    private boolean canAccessParent = false;

    /**
     * Contains the name of the tenant.
     */
    public static final Column NAME = Column.named("name");
    @Trim
    @Unique
    @Autoloaded
    @Length(255)
    private String name;

    /**
     * Contains the customer number assigned to the tenant.
     */
    public static final Column ACCOUNT_NUMBER = Column.named("accountNumber");
    @Trim
    @Unique
    @Autoloaded
    @NullAllowed
    @Length(50)
    private String accountNumber;

    /**
     * Contains the address of the tenant.
     */
    public static final Column ADDRESS = Column.named("address");
    private final InternationalAddressData address =
            new InternationalAddressData(InternationalAddressData.Requirements.NONE, null);

    /**
     * Contains the features and individual config assigned to the tenant.
     */
    public static final Column PERMISSIONS = Column.named("permissions");
    private final PermissionData permissions = new PermissionData(this);

    /**
     * Used to record changes on fields of the tenant.
     */
    public static final Column JOURNAL = Column.named("journal");
    private final JournalData journal = new JournalData(this);

    @Part
    private static Tenants tenants;

    @BeforeSave
    @BeforeDelete
    protected void onModify() {
        if (journal.hasJournaledChanges()) {
            TenantUserManager.flushCacheForTenant(this);
            tenants.flushTenantChildrenCache();
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public InternationalAddressData getAddress() {
        return address;
    }

    public PermissionData getPermissions() {
        return permissions;
    }

    public EntityRef<Tenant> getParent() {
        return parent;
    }

    public boolean isParentCanAccess() {
        return parentCanAccess;
    }

    public void setParentCanAccess(boolean parentCanAccess) {
        this.parentCanAccess = parentCanAccess;
    }

    public boolean isCanAccessParent() {
        return canAccessParent;
    }

    public void setCanAccessParent(boolean canAccessParent) {
        this.canAccessParent = canAccessParent;
    }

    @Override
    public JournalData getJournal() {
        return journal;
    }

    @Override
    public String toString() {
        if (Strings.isFilled(name)) {
            return name;
        }

        return NLS.get("Model.tenant");
    }
}
