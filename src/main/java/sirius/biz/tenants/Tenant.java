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
import sirius.mixing.Column;
import sirius.mixing.annotations.Length;
import sirius.mixing.annotations.NullAllowed;
import sirius.mixing.annotations.Trim;
import sirius.mixing.annotations.Unique;

/**
 * Created by aha on 07.05.15.
 */
public class Tenant extends BizEntity {

    @Trim
    @Unique
    @Autoloaded
    @Length(length = 255)
    private String name;
    public static final Column NAME = Column.named("name");

    @Trim
    @Unique
    @Autoloaded
    @NullAllowed
    @Length(length = 50)
    private String accountNumber;
    public static final Column ACCOUNT_NUMBER = Column.named("accountNumber");

    private final AddressData address = new AddressData();
    public static final Column ADDRESS = Column.named("address");

    private final PermissionData permissions = new PermissionData();
    public static final Column PERMISSIONS = Column.named("permissions");

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
}
