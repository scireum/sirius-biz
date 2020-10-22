/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import sirius.biz.importer.AutoImport;
import sirius.biz.model.InternationalAddressData;
import sirius.biz.mongo.PrefixSearchContent;
import sirius.biz.packages.PackageData;
import sirius.biz.protocol.JournalData;
import sirius.biz.protocol.Journaled;
import sirius.biz.web.Autoloaded;
import sirius.biz.web.BizController;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.BeforeDelete;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.Lob;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.OnValidate;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.annotations.Trim;
import sirius.db.mixing.annotations.Unique;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.kernel.settings.Settings;
import sirius.web.http.IPRange;
import sirius.web.http.WebContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Represents a tenant using the system.
 */
public class TenantData extends Composite implements Journaled {

    @Transient
    private final BaseEntity<?> tenantObject;

    /**
     * Determines if the parent tenant can become this tenant by calling "/tenants/select".
     */
    public static final Mapping PARENT_CAN_ACCESS = Mapping.named("parentCanAccess");
    @Autoloaded
    @AutoImport
    private boolean parentCanAccess = false;

    /**
     * Determines if this tenant can become its tenant by calling "/tenants/select".
     */
    public static final Mapping CAN_ACCESS_PARENT = Mapping.named("canAccessParent");
    @Autoloaded
    @AutoImport
    private boolean canAccessParent = false;

    /**
     * Determines the interval in days, after which a user needs to login again.
     */
    public static final Mapping LOGIN_INTERVAL_DAYS = Mapping.named("loginIntervalDays");
    @Autoloaded
    @AutoImport
    @NullAllowed
    private Integer loginIntervalDays;

    /**
     * Determines the interval in days, after which a user needs to login again, via an external system.
     * <p>
     * Note that this is only enforced if {@link UserAccountData#isExternalLoginRequired()} is <tt>true</tt>.
     */
    public static final Mapping EXTERNAL_LOGIN_INTERVAL_DAYS = Mapping.named("externalLoginIntervalDays");
    @Autoloaded
    @AutoImport
    @NullAllowed
    private Integer externalLoginIntervalDays;

    /**
     * Contains the display name of the tenant.
     */
    public static final Mapping NAME = Mapping.named("name");
    @Trim
    @Unique
    @Autoloaded
    @AutoImport
    @PrefixSearchContent
    @Length(255)
    private String name;

    /**
     * Contains the full company name of the tenant.
     */
    public static final Mapping FULL_NAME = Mapping.named("fullName");
    @Trim
    @Autoloaded
    @AutoImport
    @PrefixSearchContent
    @Length(255)
    @NullAllowed
    private String fullName;

    /**
     * Contains the customer number assigned to the tenant.
     */
    public static final Mapping ACCOUNT_NUMBER = Mapping.named("accountNumber");
    @Trim
    @Unique
    @Autoloaded
    @AutoImport
    @PrefixSearchContent
    @NullAllowed
    @Length(50)
    private String accountNumber;

    /**
     * Contains the name of the system which is used as the SAML provider.
     */
    public static final Mapping SAML_REQUEST_ISSUER_NAME = Mapping.named("samlRequestIssuerName");
    @Trim
    @Autoloaded
    @AutoImport
    @NullAllowed
    @Length(50)
    private String samlRequestIssuerName;

    /**
     * Contains the URL of the SAML provider.
     */
    public static final Mapping SAML_ISSUER_URL = Mapping.named("samlIssuerUrl");
    @Trim
    @Autoloaded
    @AutoImport
    @NullAllowed
    @Length(255)
    private String samlIssuerUrl;

    /**
     * Contains the endpoint index at the SAML provider.
     */
    public static final Mapping SAML_ISSUER_INDEX = Mapping.named("samlIssuerIndex");
    @Trim
    @Autoloaded
    @AutoImport
    @NullAllowed
    @Length(10)
    private String samlIssuerIndex;

    /**
     * Contains the issuer name within a SAML assertion.
     * <p>
     * If several SAML providers are used, multiple values can be separated by a <tt>,</tt>.
     */
    public static final Mapping SAML_ISSUER_NAME = Mapping.named("samlIssuerName");
    @Trim
    @Autoloaded
    @AutoImport
    @NullAllowed
    @Length(50)
    private String samlIssuerName;

    /**
     * Contains the SHA-1 fingerprint of the X509 certificate which is used to sign the SAML assertions.
     * <p>
     * If several SAML providers are used, multiple values can be separated by a <tt>,</tt>.
     */
    public static final Mapping SAML_FINGERPRINT = Mapping.named("samlFingerprint");
    @Trim
    @Autoloaded
    @AutoImport
    @NullAllowed
    @Length(255)
    private String samlFingerprint;

    /**
     * Contains the ip range which must match the current users ip.
     * <p>
     * Multiple ip ranges can be stored comma-separated.
     * Pattern of a valid ip range e.g. 192.168.192.1/32 or 192.168.168.0/24
     */
    public static final Mapping IP_RANGE = Mapping.named("ipRange");
    @Trim
    @Autoloaded
    @AutoImport
    @NullAllowed
    @Length(255)
    private String ipRange;

    /**
     * Contains the parsed ip ranges.
     */
    @Transient
    private IPRange.RangeSet rangeSet;

    /**
     * Contains a comma-separated list of roles which should be kept if the
     * user does not match the given ip range.
     */
    public static final Mapping ROLES_TO_KEEP = Mapping.named("rolesToKeep");
    @Trim
    @Autoloaded
    @AutoImport
    @NullAllowed
    @Lob
    private String rolesToKeep;

    /**
     * Contains the data from rolesToKeep but as a {@link Set}.
     */
    @Transient
    private Set<String> rolesToKeepSet;

    /**
     * Contains the address of the tenant.
     */
    public static final Mapping ADDRESS = Mapping.named("address");
    private final InternationalAddressData address = new InternationalAddressData();

    /**
     * Contains the package, upgrades and additional and revoked permissions for this tenant.
     */
    public static final Mapping PACKAGE_DATA = Mapping.named("packageData");
    private final PackageData packageData;

    /**
     * Contains a custom configuration which is added to the config of the current {@link
     * sirius.web.security.ScopeInfo}.
     */
    public static final Mapping CONFIG_STRING = Mapping.named("configString");
    @Autoloaded
    @NullAllowed
    @Lob
    private String configString;

    @Transient
    private Config config;

    /**
     * Used to record changes on fields of the tenant.
     */
    public static final Mapping JOURNAL = Mapping.named("journal");
    private final JournalData journal;

    /**
     * The language of the {@link Tenant}
     */
    public static final Mapping LANG = Mapping.named("lang");
    @NullAllowed
    @Autoloaded
    @Length(2)
    private String lang;

    @Part
    private static Tenants<?, ?, ?> tenants;

    /**
     * Creates a new instance referenced by the given entity.
     *
     * @param tenantObject the entity to which this tenant data belongs
     */
    public TenantData(BaseEntity<?> tenantObject) {
        this.tenantObject = tenantObject;
        this.journal = new JournalData(tenantObject);
        this.packageData = new PackageData(tenantObject, "tenant");
    }

    @BeforeSave
    protected void onModify() {
        if (journal.hasJournaledChanges()) {
            TenantUserManager.flushCacheForTenant((Tenant<?>) tenantObject);
            tenants.flushTenantChildrenCache();
        }

        if (Strings.isFilled(samlFingerprint)) {
            samlFingerprint = samlFingerprint.replace(" ", "").toLowerCase();
        }
    }

    @BeforeDelete
    protected void onDelete() {
        TenantUserManager.flushCacheForTenant((Tenant<?>) tenantObject);
        tenants.flushTenantChildrenCache();
    }

    @OnValidate
    protected void validate(Consumer<String> validationConsumer) {
        address.validateCountry(null, validationConsumer);
        address.validateZIP(null, validationConsumer);
        address.validateNonPartialAddress(null, validationConsumer);
    }

    /**
     * Checks if the ip of the request matches the ip range of the tenant.
     *
     * @param ctx the current request
     * @return <tt>true</tt> if the ip address matches the range or if non was configured, <tt>false</tt> otherwise
     */
    public boolean matchesIPRange(WebContext ctx) {
        if (Strings.isEmpty(ipRange)) {
            return true;
        }

        if (rangeSet == null) {
            try {
                rangeSet = IPRange.parseRangeSet(ipRange);
            } catch (IllegalArgumentException e) {
                // if an invalid range was configured we can not remove any permission
                Exceptions.ignore(e);
                return true;
            }
        }

        return rangeSet.accepts(ctx.getRemoteIP());
    }

    /**
     * Calculates all roles the user should keep when the ip range check fails.
     *
     * @return {@link Set} holding the roles to keep
     */
    public Set<String> getRolesToKeepAsSet() {
        if (rolesToKeepSet == null) {
            compileRolesToKeep();
        }

        return Collections.unmodifiableSet(rolesToKeepSet);
    }

    protected void compileRolesToKeep() {
        if (Strings.isEmpty(rolesToKeep)) {
            rolesToKeepSet = Collections.emptySet();
            return;
        }
        rolesToKeepSet = new TreeSet<>();
        for (String permission : rolesToKeep.split(",")) {
            permission = permission.trim();
            if (Strings.isFilled(permission)) {
                rolesToKeepSet.add(permission);
            }
        }
    }

    @Override
    public String toString() {
        if (Strings.isFilled(name)) {
            return name;
        }

        return NLS.get("Model.tenant");
    }

    /**
     * Returns the parsed config for the tenant.
     *
     * @return the parsed configuration, or <tt>null</tt> if the config is empty
     */
    @Nullable
    public Config getConfig() {
        if (config == null) {
            if (Strings.isFilled(configString)) {
                try {
                    config = ConfigFactory.parseString(configString);
                } catch (Exception e) {
                    throw Exceptions.handle()
                                    .to(BizController.LOG)
                                    .error(e)
                                    .withSystemErrorMessage("Cannot load config of %s (%s): %s (%s)",
                                                            tenantObject,
                                                            tenantObject.getId())
                                    .handle();
                }
            } else {
                return null;
            }
        }

        return config;
    }

    /**
     * Returns the parsed settings for the tenant
     *
     * @return the parsed configuration
     */
    @Nonnull
    public Settings getSettings() {
        Config config = getConfig();

        if (config != null) {
            return new Settings(config, false);
        }

        return new Settings(ConfigFactory.empty(), false);
    }

    /**
     * Returns the config as string.
     *
     * @return the individual config as string
     */
    public String getConfigString() {
        return configString;
    }

    /**
     * Sets the config as string.
     *
     * @param configString the individual config as string
     */
    public void setConfigString(String configString) {
        this.configString = configString;
        this.config = null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
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

    public String getIpRange() {
        return ipRange;
    }

    public void setIpRange(String ipRange) {
        this.ipRange = ipRange;
    }

    public String getRolesToKeep() {
        return rolesToKeep;
    }

    public void setRolesToKeep(String rolesToKeep) {
        this.rolesToKeep = rolesToKeep;
    }

    @Override
    public JournalData getJournal() {
        return journal;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public PackageData getPackageData() {
        return packageData;
    }
}
