/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.packages.Packages;
import sirius.biz.protocol.AuditLog;
import sirius.biz.web.BasePageHelper;
import sirius.biz.web.BizController;
import sirius.biz.web.SaveHelper;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.controller.AutocompleteHelper;
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Message;
import sirius.web.controller.Page;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.security.Permission;
import sirius.web.security.Permissions;
import sirius.web.security.UserContext;
import sirius.web.services.JSONStructuredOutput;
import sirius.web.util.LinkBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Provides the database independent controller for tenants.
 * <p>
 * Some specific behaviour which depends on the underlying database has to be implemented by a concrete subclass.
 *
 * @param <I> the type of database IDs used by the concrete implementation
 * @param <T> specifies the effective entity type used to represent Tenants
 * @param <U> specifies the effective entity type used to represent UserAccounts
 */
public abstract class TenantController<I, T extends BaseEntity<I> & Tenant<I>, U extends BaseEntity<I> & UserAccount<I, T>>
        extends BizController {

    /**
     * Contains the permission required to manage tenants.
     */
    public static final String PERMISSION_MANAGE_TENANTS = "permission-manage-tenants";

    /**
     * Contains the scope of packages and upgrades to use.
     */
    public static final String PACKAGE_SCOPE_TENANT = "tenant";

    /**
     * Contains a list of all available features or permission which can be granted to a tenant.
     */
    @ConfigValue("security.tenantPermissions")
    protected List<String> permissions;

    @ConfigValue("product.wondergemRoot")
    protected String wondergemRoot;

    @Part
    protected AuditLog auditLog;

    @Part
    private Packages packages;

    /**
     * Returns all available features which can be assigned to a tenant.
     *
     * @return an unmodifyable list of all features available.
     */
    public List<String> getPermissions() {
        return Collections.unmodifiableList(permissions);
    }

    /**
     * Returns a translated name of the given permission.
     *
     * @param role the permission or feature to translate
     * @return a translated name for the permission
     */
    public String getPermissionName(String role) {
        return Permissions.getTranslatedPermission(role);
    }

    /**
     * Returns a translated description of the given permission.
     *
     * @param role the permission for which a description is requested
     * @return the translated description of the permission
     */
    public String getPermissionDescription(String role) {
        return Permissions.getPermissionDescription(role);
    }

    /**
     * Lists all tenants known to the system.
     *
     * @param webContext the current request
     */
    @Routed("/tenants")
    @DefaultRoute
    @Permission(PERMISSION_MANAGE_TENANTS)
    public void tenants(WebContext webContext) {
        webContext.respondWith()
                  .template("/templates/biz/tenants/tenants.html.pasta", getTenantsAsPage(webContext).asPage(), this);
    }

    /**
     * Constructs a page helper for the tenants to view.
     *
     * @return the list of available tenants wrapped as page helper
     */
    protected abstract BasePageHelper<T, ?, ?, ?> getTenantsAsPage(WebContext webContext);

    /**
     * Provides an editor for updating a tenant.
     *
     * @param webContext the current request
     * @param tenantId   the id of the tenant to change
     */
    @Routed("/tenant/:1")
    @Permission(PERMISSION_MANAGE_TENANTS)
    public void tenant(WebContext webContext, String tenantId) {
        T tenant = find(getTenantClass(), tenantId);

        SaveHelper saveHelper = prepareSave(webContext);
        if (tenant.getTenantData().getPackageData().hasAvailablePackagesOrUpgrades()) {
            saveHelper.withAfterCreateURI("/tenant/${id}/package");
        } else {
            saveHelper.withAfterCreateURI("/tenant/${id}");
        }
        boolean requestHandled = saveHelper.saveEntity(tenant);
        if (!requestHandled) {
            validate(tenant);

            webContext.respondWith().template("/templates/biz/tenants/tenant-details.html.pasta", tenant, this);
        }
    }

    /**
     * Provides an editor for updating a tenant.
     *
     * @param webContext the current request
     * @param tenantId   the id of the tenant to change
     */
    @Routed("/tenant/:1/package")
    @Permission(PERMISSION_MANAGE_TENANTS)
    public void packageAndUpgrades(WebContext webContext, String tenantId) {
        T tenant = find(getTenantClass(), tenantId);
        assertNotNew(tenant);

        SaveHelper saveHelper = prepareSave(webContext);
        saveHelper.withPreSaveHandler(isNew -> {
            tenant.getTenantData()
                  .getPackageData()
                  .loadPackageAndUpgrades(PACKAGE_SCOPE_TENANT,
                                          webContext.getParameters("upgrades"),
                                          webContext.get("package").asString());
        });

        boolean requestHandled = saveHelper.saveEntity(tenant);
        if (!requestHandled) {
            validate(tenant);

            webContext.respondWith().template("/templates/biz/tenants/tenant-package.html.pasta", tenant, this);
        }
    }

    /**
     * Provides an editor for updating a tenant.
     *
     * @param webContext the current request
     * @param tenantId   the id of the tenant to change
     */
    @Routed("/tenant/:1/extended")
    @Permission(PERMISSION_MANAGE_TENANTS)
    public void extended(WebContext webContext, String tenantId) {
        T tenant = find(getTenantClass(), tenantId);

        boolean requestHandled = prepareSave(webContext).saveEntity(tenant);
        if (!requestHandled) {
            validate(tenant);
            webContext.respondWith().template("/templates/biz/tenants/tenant-extended.html.pasta", tenant, this);
        }
    }

    /**
     * Provides access to the package manager (used by the template).
     *
     * @return the packages helper
     */
    public Packages getPackages() {
        return packages;
    }

    /**
     * Returns a list of supported languages and their translated name.
     *
     * @return a list of tuples containing the ISO code and the translated name
     */
    public List<Tuple<String, String>> getAvailableLanguages() {
        return tenants.getTenantUserManager().getAvailableLanguages();
    }

    /**
     * Returns the effective entity class used to represent tenants.
     *
     * @return the effective entity class for tenants
     */
    @SuppressWarnings("unchecked")
    protected Class<T> getTenantClass() {
        return (Class<T>) tenants.getTenantClass();
    }

    /**
     * Provides an editor for changing the config of a tenant.
     *
     * @param webContext the current request
     * @param tenantId   the id of the tenant to change
     */
    @Routed("/tenant/:1/config")
    @Permission(PERMISSION_MANAGE_TENANTS)
    public void tenantConfig(WebContext webContext, String tenantId) {
        T tenant = find(getTenantClass(), tenantId);
        assertNotNew(tenant);
        webContext.respondWith().template("/templates/biz/tenants/tenant-config.html.pasta", tenant, this);
    }

    /**
     * Provides a JSON API to change the configuration of a tenant without altering other fields.
     *
     * @param webContext the current request
     * @param jsonOutput the JSON response being generated
     * @param tenantId   the id of the tenant whose config should be updated
     */
    @Routed(value = "/tenant/:1/config/update", jsonCall = true)
    @Permission(PERMISSION_MANAGE_TENANTS)
    public void updateTenantConfig(WebContext webContext, JSONStructuredOutput jsonOutput, String tenantId) {
        T tenant = find(getTenantClass(), tenantId);
        assertNotNew(tenant);

        String configFieldName = Tenant.TENANT_DATA.inner(TenantData.CONFIG_STRING).getName();
        if (webContext.hasParameter(configFieldName)) {
            // Reads configuration manually to prevent altering other fields
            String config = webContext.getParameter(configFieldName);
            tenant.getTenantData().setConfigString(config);
            // parses the config to make sure it is valid
            tenant.getTenantData().getConfig();
        }

        tenant.getMapper().update(tenant);
    }

    /**
     * Provides an editor for setting additional and revoked permissions.
     *
     * @param webContext the current request
     * @param tenantId   the id of the tenant to change
     */
    @Routed("/tenant/:1/permissions")
    @Permission(PERMISSION_MANAGE_TENANTS)
    public void permissions(WebContext webContext, String tenantId) {
        T tenant = find(getTenantClass(), tenantId);
        assertNotNew(tenant);

        boolean handled = prepareSave(webContext).disableAutoload().withPreSaveHandler(isNew -> {
            tenant.getTenantData()
                  .getPackageData()
                  .loadRevokedAndAdditionalPermission(getPermissions(), webContext::getParameter);
        }).saveEntity(tenant);

        if (!handled) {
            webContext.respondWith().template("/templates/biz/tenants/tenant-permissions.html.pasta", tenant, this);
        }
    }

    /**
     * Provides an editor for setting additional and revoked permissions.
     *
     * @param webContext the current request
     * @param tenantId   the id of the tenant to change
     */
    @Routed("/tenant/:1/saml")
    @LoginRequired
    @Permission(PERMISSION_MANAGE_TENANTS)
    public void saml(WebContext webContext, String tenantId) {
        T tenant = find(getTenantClass(), tenantId);
        assertNotNew(tenant);

        boolean handled = prepareSave(webContext).disableAutoload()
                                                 .withMappings(Tenant.TENANT_DATA.inner(TenantData.SAML_REQUEST_ISSUER_NAME),
                                                               Tenant.TENANT_DATA.inner(TenantData.SAML_ISSUER_INDEX),
                                                               Tenant.TENANT_DATA.inner(TenantData.SAML_ISSUER_URL),
                                                               Tenant.TENANT_DATA.inner(TenantData.SAML_ISSUER_NAME),
                                                               Tenant.TENANT_DATA.inner(TenantData.SAML_FINGERPRINT))
                                                 .saveEntity(tenant);

        if (!handled) {
            webContext.respondWith().template("/templates/biz/tenants/tenant-saml.html.pasta", tenant, this);
        }
    }

    /**
     * Returns the uri to the tenant delete job.
     *
     * @param tenantId the id of the tenant to delete
     * @return the uri to the job config page
     */
    @SuppressWarnings("squid:S1192")
    @Explain("This string has a completely different semantic than the constant defined above")
    public String getDeleteLink(String tenantId) {
        return new LinkBuilder("/job/delete-tenant").append("tenant", tenantId).toString();
    }

    /**
     * Lists all tenants which the current user can "become" (switch to).
     *
     * @param webContext the current request
     */
    @Routed("/tenants/select")
    @LoginRequired
    @Permission(TenantUserManager.PERMISSION_SELECT_TENANT)
    public void selectTenants(WebContext webContext) {
        Page<T> tenants = getSelectableTenantsAsPage(webContext, determineCurrentTenant(webContext)).asPage();
        webContext.respondWith()
                  .template("/templates/biz/tenants/select-tenant.html.pasta", tenants, isCurrentlySpying(webContext));
    }

    private boolean isCurrentlySpying(WebContext webContext) {
        return webContext.getSessionValue(UserContext.getCurrentScope().getScopeId()
                                          + TenantUserManager.TENANT_SPY_ID_SUFFIX).isFilled();
    }

    /**
     * Determines the currently active tenant.
     * <p>
     * If "spying" is active, this will still return the underlying original tenant.
     *
     * @param webContext the request to load the session data from
     * @return the original tenant which is logged in
     */
    public T determineCurrentTenant(WebContext webContext) {
        String tenantId = determineOriginalTenantId(webContext);
        return mixing.getDescriptor(getTenantClass())
                     .getMapper()
                     .find(getTenantClass(), tenantId)
                     .orElseThrow(() -> Exceptions.createHandled()
                                                  .withSystemErrorMessage("Cannot determine current tenant!")
                                                  .handle());
    }

    @SuppressWarnings("unchecked")
    private String determineOriginalTenantId(WebContext webContext) {
        return ((TenantUserManager<I, T, U>) UserContext.get().getUserManager()).getOriginalTenantId(webContext);
    }

    /**
     * Tries to resolve the given ID into an accessible tenant.
     * <p>
     * Accessible means, that the current user (its tenant) is either the system tenant or the parent tenant of the
     * one to resolve.
     *
     * @param id            the id to resolve into a tenant
     * @param currentTenant the current tenant
     * @return the tenant object for the id wrapped as optional or an empty optional if the currentTenant may not
     * access the given tenant
     */
    public abstract Optional<T> resolveAccessibleTenant(String id, Tenant<?> currentTenant);

    /**
     * Constructs a page helper for the selectable tenants.
     *
     * @return the list of selectable tenants wrapped as page helper
     */
    protected abstract BasePageHelper<T, ?, ?, ?> getSelectableTenantsAsPage(WebContext webContext, T currentTenant);

    /**
     * Makes the current user belong to the given tenant.
     * <p>
     * The user is will keep the permissions tied to its user,
     * but the permissions granted by his tenant will change to the new tenant.
     * Exception to this is {@link Tenant#PERMISSION_SYSTEM_TENANT}, this will be kept if the user originally belonged to the system tenant.
     * Additionatly, the permission {@link TenantUserManager#PERMISSION_SPY_USER} is given, so the system can identify the tenant switch.
     *
     * @param webContext the current request
     * @param tenantId   the id of the tenant to switch to
     */
    @LoginRequired
    @Routed("/tenants/select/:1")
    public void selectTenant(final WebContext webContext, String tenantId) {
        if ("main".equals(tenantId) || Strings.areEqual(determineOriginalTenantId(webContext), tenantId)) {
            String originalUserId = tenants.getTenantUserManager().getOriginalUserId();
            UserAccount<?, ?> account = tenants.getTenantUserManager().fetchAccount(originalUserId);
            auditLog.neutral("AuditLog.switchedToMainTenant")
                    .hideFromUser()
                    .causedByUser(account.getUniqueName(), account.getUserAccountData().getLogin().getUsername())
                    .forUser(account.getUniqueName(), account.getUserAccountData().getLogin().getUsername())
                    .forTenant(account.getTenant().getIdAsString(),
                               account.getTenant().fetchValue().getTenantData().getName())
                    .log();

            webContext.setSessionValue(UserContext.getCurrentScope().getScopeId()
                                       + TenantUserManager.TENANT_SPY_ID_SUFFIX, null);
            webContext.respondWith().redirectTemporarily(webContext.get("goto").asString(wondergemRoot));
            return;
        }

        assertPermission(TenantUserManager.PERMISSION_SELECT_TENANT);

        Optional<T> tenant = resolveAccessibleTenant(tenantId, determineCurrentTenant(webContext));
        if (!tenant.isPresent()) {
            UserContext.get().addMessage(Message.error(NLS.get("TenantController.cannotBecomeTenant")));
            selectTenants(webContext);
            return;
        }

        T effectiveTenant = tenant.get();
        auditLog.neutral("AuditLog.selectedTenant")
                .hideFromUser()
                .causedByCurrentUser()
                .forCurrentUser()
                .forTenant(effectiveTenant.getIdAsString(), effectiveTenant.getTenantData().getName())
                .log();

        webContext.setSessionValue(UserContext.getCurrentScope().getScopeId() + TenantUserManager.TENANT_SPY_ID_SUFFIX,
                                   effectiveTenant.getIdAsString());

        webContext.respondWith().redirectTemporarily(webContext.get("goto").asString(wondergemRoot));
    }

    /**
     * Autocompletion for Tenants.
     *
     * @param webContext the current request
     */
    @LoginRequired
    @Routed("/tenants/autocomplete")
    public void tenantsAutocomplete(final WebContext webContext) {
        AutocompleteHelper.handle(webContext, (query, result) -> {
            Page<T> tenants = getSelectableTenantsAsPage(webContext, determineCurrentTenant(webContext)).asPage();

            tenants.getItems().forEach(tenant -> {
                result.accept(new AutocompleteHelper.Completion(tenant.getIdAsString(),
                                                                tenant.toString(),
                                                                tenant.toString()));
            });
        });
    }
}
