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
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.controller.AutocompleteHelper;
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Message;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.security.Permission;
import sirius.web.security.Permissions;
import sirius.web.security.UserContext;
import sirius.web.services.JSONStructuredOutput;

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
     * @param ctx the current request
     */
    @Routed("/tenants")
    @DefaultRoute
    @Permission(PERMISSION_MANAGE_TENANTS)
    public void tenants(WebContext ctx) {
        ctx.respondWith().template("/templates/biz/tenants/tenants.html.pasta", getTenantsAsPage(ctx).asPage());
    }

    /**
     * Constructs a page helper for the tenants to view.
     *
     * @return the list of available tenants wrapped as page helper
     */
    protected abstract BasePageHelper<T, ?, ?, ?> getTenantsAsPage(WebContext ctx);

    /**
     * Provides an editor for updating a tenant.
     *
     * @param ctx      the current request
     * @param tenantId the id of the tenant to change
     */
    @Routed("/tenant/:1")
    @Permission(PERMISSION_MANAGE_TENANTS)
    public void tenant(WebContext ctx, String tenantId) {
        T tenant = find(getTenantClass(), tenantId);

        SaveHelper saveHelper = prepareSave(ctx).withAfterCreateURI("/tenant/${id}");
        saveHelper.withPreSaveHandler(isNew -> {
            tenant.getTenantData()
                  .getPackageData()
                  .loadPackageAndUpgrades(PACKAGE_SCOPE_TENANT,
                                          ctx.getParameters("upgrades"),
                                          ctx.get("package").asString());
        });

        boolean requestHandled = saveHelper.saveEntity(tenant);
        if (!requestHandled) {
            validate(tenant);

            ctx.respondWith().template("/templates/biz/tenants/tenant-details.html.pasta", tenant, this);
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
     * Provides a JSON API to change the settings of a tenant, including its configuration.
     *
     * @param ctx      the current request
     * @param out      the JSON response being generated
     * @param tenantId the id of the tenant to update
     */
    @Routed(value = "/tenant/:1/update", jsonCall = true)
    @Permission(PERMISSION_MANAGE_TENANTS)
    public void tenantUpdate(WebContext ctx, JSONStructuredOutput out, String tenantId) {
        T tenant = find(getTenantClass(), tenantId);
        assertNotNew(tenant);
        load(ctx, tenant);
        if (ctx.hasParameter(Tenant.TENANT_DATA.inner(TenantData.CONFIG_STRING).getName())) {
            // parses the config to make sure it is valid
            tenant.getTenantData().getConfig();
        }
        tenant.getMapper().update(tenant);
    }

    /**
     * Provides an editor for changing the config of a tenant.
     *
     * @param ctx      the current request
     * @param tenantId the id of the tenant to change
     */
    @Routed("/tenant/:1/config")
    @Permission(PERMISSION_MANAGE_TENANTS)
    public void tenantConfig(WebContext ctx, String tenantId) {
        T tenant = find(getTenantClass(), tenantId);
        assertNotNew(tenant);
        ctx.respondWith().template("/templates/biz/tenants/tenant-config.html.pasta", tenant, this);
    }

    /**
     * Provides an editor for setting additional and revoked permissions.
     *
     * @param ctx      the current request
     * @param tenantId the id of the tenant to change
     */
    @Routed("/tenant/:1/permissions")
    @Permission(PERMISSION_MANAGE_TENANTS)
    public void permissions(WebContext ctx, String tenantId) {
        T tenant = find(getTenantClass(), tenantId);
        assertNotNew(tenant);

        boolean handled = prepareSave(ctx).disableAutoload().withPreSaveHandler(isNew -> {
            tenant.getTenantData()
                  .getPackageData()
                  .loadRevokedAndAdditionalPermission(getPermissions(), ctx::getParameter);
        }).saveEntity(tenant);

        if (!handled) {
            ctx.respondWith().template("/templates/biz/tenants/tenant-permissions.html.pasta", tenant, this);
        }
    }

    /**
     * Provides an editor for setting additional and revoked permissions.
     *
     * @param ctx      the current request
     * @param tenantId the id of the tenant to change
     */
    @Routed("/tenant/:1/saml")
    @LoginRequired
    @Permission(PERMISSION_MANAGE_TENANTS)
    public void saml(WebContext ctx, String tenantId) {
        T tenant = find(getTenantClass(), tenantId);
        assertNotNew(tenant);

        boolean handled = prepareSave(ctx).disableAutoload()
                                          .withMappings(Tenant.TENANT_DATA.inner(TenantData.SAML_REQUEST_ISSUER_NAME),
                                                        Tenant.TENANT_DATA.inner(TenantData.SAML_ISSUER_INDEX),
                                                        Tenant.TENANT_DATA.inner(TenantData.SAML_ISSUER_URL),
                                                        Tenant.TENANT_DATA.inner(TenantData.SAML_ISSUER_NAME),
                                                        Tenant.TENANT_DATA.inner(TenantData.SAML_FINGERPRINT))
                                          .saveEntity(tenant);

        if (!handled) {
            ctx.respondWith().template("/templates/biz/tenants/tenant-saml.html.pasta", tenant, this);
        }
    }

    /**
     * Deletes the given tenant and returns the list of tenants.
     *
     * @param ctx      the current request
     * @param tenantId the id of the tenant to delete
     */
    @Routed("/tenant/:1/delete")
    @LoginRequired
    @Permission(PERMISSION_MANAGE_TENANTS)
    public void deleteTenant(WebContext ctx, String tenantId) {
        Optional<T> optionalTenant =
                mixing.getDescriptor(getTenantClass()).getMapper().find(getTenantClass(), tenantId);

        optionalTenant.ifPresent(tenant -> {
            if (tenant.equals(tenants.getRequiredTenant())) {
                throw Exceptions.createHandled().withNLSKey("TenantController.cannotDeleteSelf").handle();
            }
        });

        deleteEntity(ctx, optionalTenant);
        tenants(ctx);
    }

    /**
     * Lists all tenants which the current user can "become" (switch to).
     *
     * @param ctx the current request
     */
    @Routed("/tenants/select")
    @LoginRequired
    @Permission(TenantUserManager.PERMISSION_SELECT_TENANT)
    public void selectTenants(WebContext ctx) {
        BasePageHelper<T, ?, ?, ?> ph = getSelectableTenantsAsPage(ctx, determineCurrentTenant(ctx));
        ctx.respondWith()
           .template("/templates/biz/tenants/select-tenant.html.pasta", ph.asPage(), isCurrentlySpying(ctx));
    }

    private boolean isCurrentlySpying(WebContext ctx) {
        return ctx.getSessionValue(UserContext.getCurrentScope().getScopeId() + TenantUserManager.TENANT_SPY_ID_SUFFIX)
                  .isFilled();
    }

    /**
     * Determines the currently active tenant.
     * <p>
     * If "spying" is active, this will still return the underlying original tenant.
     *
     * @param ctx the request to load the session data from
     * @return the original tenant which is logged in
     */
    public T determineCurrentTenant(WebContext ctx) {
        String tenantId = determineOriginalTenantId(ctx);
        return mixing.getDescriptor(getTenantClass())
                     .getMapper()
                     .find(getTenantClass(), tenantId)
                     .orElseThrow(() -> Exceptions.createHandled()
                                                  .withSystemErrorMessage("Cannot determine current tenant!")
                                                  .handle());
    }

    @SuppressWarnings("unchecked")
    private String determineOriginalTenantId(WebContext ctx) {
        return ((TenantUserManager<I, T, U>) UserContext.get().getUserManager()).getOriginalTenantId(ctx);
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
    protected abstract BasePageHelper<T, ?, ?, ?> getSelectableTenantsAsPage(WebContext ctx, T currentTenant);

    /**
     * Makes the current user belong to the given tenant.
     * <p>
     * The user is will keep the permissions tied to its user,
     * but the permissions granted by his tenant will change to the new tenant.
     * Exception to this is {@link Tenant#PERMISSION_SYSTEM_TENANT}, this will be kept if the user originally belonged to the system tenant.
     * Additionatly, the permission {@link TenantUserManager#PERMISSION_SPY_USER} is given, so the system can identify the tenant switch.
     *
     * @param ctx the current request
     * @param id  the id of the tenant to switch to
     */
    @LoginRequired
    @Routed("/tenants/select/:1")
    public void selectTenant(final WebContext ctx, String id) {
        if ("main".equals(id) || Strings.areEqual(determineOriginalTenantId(ctx), id)) {
            String originalUserId = tenants.getTenantUserManager().getOriginalUserId();
            UserAccount<?, ?> account = tenants.getTenantUserManager().fetchAccount(originalUserId);
            auditLog.neutral("AuditLog.switchedToMainTenant")
                    .hideFromUser()
                    .causedByUser(account.getUniqueName(), account.getUserAccountData().getLogin().getUsername())
                    .forUser(account.getUniqueName(), account.getUserAccountData().getLogin().getUsername())
                    .forTenant(account.getTenant().getIdAsString(),
                               account.getTenant().getValue().getTenantData().getName())
                    .log();

            ctx.setSessionValue(UserContext.getCurrentScope().getScopeId() + TenantUserManager.TENANT_SPY_ID_SUFFIX,
                                null);
            ctx.respondWith().redirectTemporarily(ctx.get("goto").asString(wondergemRoot));
            return;
        }

        assertPermission(TenantUserManager.PERMISSION_SELECT_TENANT);

        Optional<T> tenant = resolveAccessibleTenant(id, determineCurrentTenant(ctx));
        if (!tenant.isPresent()) {
            UserContext.get().addMessage(Message.error(NLS.get("TenantController.cannotBecomeTenant")));
            selectTenants(ctx);
            return;
        }

        T effectiveTenant = tenant.get();
        auditLog.neutral("AuditLog.selectedTenant")
                .hideFromUser()
                .causedByCurrentUser()
                .forCurrentUser()
                .forTenant(effectiveTenant.getIdAsString(), effectiveTenant.getTenantData().getName())
                .log();

        ctx.setSessionValue(UserContext.getCurrentScope().getScopeId() + TenantUserManager.TENANT_SPY_ID_SUFFIX,
                            effectiveTenant.getIdAsString());

        ctx.respondWith().redirectTemporarily(ctx.get("goto").asString(wondergemRoot));
    }

    /**
     * Autocompletion for Tenants.
     *
     * @param ctx the current request
     */
    @LoginRequired
    @Routed("/tenants/autocomplete")
    public void tenantsAutocomplete(final WebContext ctx) {
        AutocompleteHelper.handle(ctx, (query, result) -> {
            BasePageHelper<T, ?, ?, ?> ph = getSelectableTenantsAsPage(ctx, determineCurrentTenant(ctx));

            ph.asPage().getItems().forEach(tenant -> {
                result.accept(new AutocompleteHelper.Completion(tenant.getIdAsString(),
                                                                tenant.toString(),
                                                                tenant.toString()));
            });
        });
    }
}
