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
@Framework("biz.tenants")
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
     * Determines the interval in days, after which a user needs to login again.
     */
    public static final Column LOGIN_INTERVAL_DAYS = Column.named("loginIntervalDays");
    @Autoloaded
    @NullAllowed
    private Integer loginIntervalDays;

    /**
     * Determines the interval in days, after which a user needs to login again, via an extenal system.
     * <p>
     * Note that this is only enforced if {@link UserAccount#externalLoginRequired} is <tt>true</tt>.
     */
    public static final Column EXTERNAL_LOGIN_INTERVAL_DAYS = Column.named("externalLoginIntervalDays");
    @Autoloaded
    @NullAllowed
    private Integer externalLoginIntervalDays;

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
     * Contains the name of the system which is used as the SAML provider.
     */
    public static final Column SAML_REQUEST_ISSUER_NAME = Column.named("samlRequestIssuerName");
    @Trim
    @Autoloaded
    @NullAllowed
    @Length(50)
    private String samlRequestIssuerName;

    /**
     * Contains the URL of the SAML provider.
     */
    public static final Column SAML_ISSUER_URL = Column.named("samlIssuerUrl");
    @Trim
    @Autoloaded
    @NullAllowed
    @Length(255)
    private String samlIssuerUrl;

    /**
     * Contains the endpoint index at the SAML provider.
     */
    public static final Column SAML_ISSUER_INDEX = Column.named("samlIssuerIndex");
    @Trim
    @Autoloaded
    @NullAllowed
    @Length(10)
    private String samlIssuerIndex;

    /**
     * Contains the issuer name within a SAML assertion.
     * <p>
     * If several SAML providers are used, multiple values can be separated by a <tt>,</tt>.
     */
    public static final Column SAML_ISSUER_NAME = Column.named("samlIssuerName");
    @Trim
    @Autoloaded
    @NullAllowed
    @Length(50)
    private String samlIssuerName;

    /**
     * Contains the SHA-1 fingerprint of the X509 certificate which is used to sign the SAML assertions.
     * <p>
     * If several SAML providers are used, multiple values can be separated by a <tt>,</tt>.
     */
    public static final Column SAML_FINGERPRINT = Column.named("samlFingerprint");
    @Trim
    @Autoloaded
    @NullAllowed
    @Length(255)
    private String samlFingerprint;

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

        if (Strings.isFilled(samlFingerprint)) {
            samlFingerprint = samlFingerprint.replace(" ", "").toLowerCase();
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

    public String getSamlIssuerName() {
        return samlIssuerName;
    }

    public void setSamlIssuerName(String samlIssuerName) {
        this.samlIssuerName = samlIssuerName;
    }

    public String getSamlIssuerUrl() {
        return samlIssuerUrl;
    }

    public void setSamlIssuerUrl(String samlIssuerUrl) {
        this.samlIssuerUrl = samlIssuerUrl;
    }

    public String getSamlIssuerIndex() {
        return samlIssuerIndex;
    }

    public void setSamlIssuerIndex(String samlIssuerIndex) {
        this.samlIssuerIndex = samlIssuerIndex;
    }

    public String getSamlFingerprint() {
        return samlFingerprint;
    }

    public void setSamlFingerprint(String samlFingerprint) {
        this.samlFingerprint = samlFingerprint;
    }

    public String getSamlRequestIssuerName() {
        return samlRequestIssuerName;
    }

    public void setSamlRequestIssuerName(String samlRequestIssuerName) {
        this.samlRequestIssuerName = samlRequestIssuerName;
    }

    public Integer getLoginIntervalDays() {
        return loginIntervalDays;
    }

    public void setLoginIntervalDays(Integer loginIntervalDays) {
        this.loginIntervalDays = loginIntervalDays;
    }

    public Integer getExternalLoginIntervalDays() {
        return externalLoginIntervalDays;
    }

    public void setExternalLoginIntervalDays(Integer externalLoginIntervalDays) {
        this.externalLoginIntervalDays = externalLoginIntervalDays;
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
