/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.model.AddressData;
import sirius.biz.model.BizEntity;
import sirius.biz.model.PermissionData;
import sirius.biz.web.Autoloaded;
import sirius.db.mixing.Column;
import sirius.db.mixing.annotations.BeforeDelete;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Trim;
import sirius.db.mixing.annotations.Unique;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Framework;
import sirius.kernel.nls.NLS;

/**
 * Represents a tenant using the system.
 * <p>
 * Helps to support multi tenancy for SaaS platforms.
 */
@Framework("tenants")
public class Tenant extends BizEntity {

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
    private final AddressData address = new AddressData(AddressData.Requirements.NONE, null);

    /**
     * Contains the features and individual config assigned to the tenant.
     */
    public static final Column PERMISSIONS = Column.named("permissions");
    private final PermissionData permissions = new PermissionData(this);

    @BeforeSave
    @BeforeDelete
    protected void onModify() {
        TenantUserManager.flushCacheForTenant(this);
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

    public AddressData getAddress() {
        return address;
    }

    public PermissionData getPermissions() {
        return permissions;
    }

    @Override
    public String toString() {
        if (Strings.isFilled(name)) {
            return name;
        }

        return NLS.get("Model.tenant");
    }
}
