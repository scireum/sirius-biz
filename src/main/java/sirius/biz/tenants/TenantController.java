/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.model.AddressData;
import sirius.biz.packages.Packages;
import sirius.biz.model.PermissionData;
import sirius.biz.protocol.AuditLog;
import sirius.biz.web.BasePageHelper;
import sirius.biz.web.BizController;
import sirius.biz.web.SaveHelper;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Strings;
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
import sirius.web.security.UserContext;
import sirius.web.services.JSONStructuredOutput;

import java.util.ArrayList;
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
        return NLS.get("TenantPermission." + role);
    }

    /**
     * Returns a translated description of the given permission.
     *
     * @param role the permission for which a description is requested
     * @return the translated description of the permission
     */
    public String getPermissionDescription(String role) {
        return NLS.getIfExists("TenantPermission." + role + ".description", null).orElse("");
    }

    /**
     * Lists all tenants known to the system.
     *
     * @param ctx the current request
     */
    @Routed("/tenants")
    @DefaultRoute
    @LoginRequired
    @Permission(PERMISSION_MANAGE_TENANTS)
    public void tenants(WebContext ctx) {
        BasePageHelper<T, ?, ?, ?> ph = getTenantsAsPage();
        ph.withContext(ctx);
        ctx.respondWith().template("templates/biz/tenants/tenants.html.pasta", ph.asPage());
    }

    /**
     * Constructs a page helper for the tenants to view.
     *
     * @return the list of available tenants wrapped as page helper
     */
    protected abstract BasePageHelper<T, ?, ?, ?> getTenantsAsPage();

    /**
     * Provides an editor for updating a tenant.
     *
     * @param ctx      the current request
     * @param tenantId the id of the tenant to change
     */
    @Routed("/tenant/:1")
    @LoginRequired
    @Permission(PERMISSION_MANAGE_TENANTS)
    public void tenant(WebContext ctx, String tenantId) {
        T tenant = find(getTenantClass(), tenantId);

        SaveHelper saveHelper = prepareSave(ctx).withAfterCreateURI("/tenant/${id}").withAfterSaveURI("/tenants");
        saveHelper.withPreSaveHandler(isNew -> {
            tenant.getTenantData().getPackageData().getUpgrades().clear();
            for (String upgrade : ctx.getParameters("upgrades")) {
                if (packages.getUpgrades("tenant").contains(upgrade)) {
                    // ensure only real upgrades get in this list
                    tenant.getTenantData().getPackageData().getUpgrades().add(upgrade);
                }
            }

            if (packages.getPackages("tenant").contains(ctx.get("package").asString())) {
                // ensure only real packages get in this field
                tenant.getTenantData().getPackageData().setPackage(ctx.get("package").asString());
            }
        });

        boolean requestHandled = saveHelper.saveEntity(tenant);
        if (!requestHandled) {
            validate(tenant);

            List<String> availableLanguages =
                    ((TenantUserManager<?, ?, ?>) UserContext.get().getUserManager()).getAvailableLanguages();
            List<BaseEntity<?>> possibleParentTenants = mixing.getDescriptor(getTenantClass())
                                                              .getMapper()
                                                              .select(getTenantClass())
                                                              .orderAsc(Tenant.TENANT_DATA.inner(TenantData.NAME))
                                                              .queryList();

            ctx.respondWith()
               .template("templates/biz/tenants/tenant-details.html.pasta",
                         tenant,
                         this,
                         possibleParentTenants,
                         availableLanguages,
                         packages);
        }
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
    @LoginRequired
    @Permission(PERMISSION_MANAGE_TENANTS)
    public void tenantUpdate(WebContext ctx, JSONStructuredOutput out, String tenantId) {
        T tenant = find(getTenantClass(), tenantId);
        assertNotNew(tenant);
        load(ctx, tenant);
        if (ctx.hasParameter(Tenant.TENANT_DATA.inner(TenantData.CONFIG_STRING).getName())) {
            tenant.getTenantData().getConfig();
        }
        tenant.getTenantData().getPackageData().getAdditionalPermissions().clear();
        for (String permission : ctx.getParameters("permissions")) {
            // Ensure that only real roles end up in the permissions list,
            // as roles, permissions and flags later end up in the same vector
            // therefore we don't want nothing else but tenant permissions in this list
            if (getPermissions().contains(permission)) {
                tenant.getTenantData().getPackageData().getAdditionalPermissions().add(permission);
            }
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
    @LoginRequired
    @Permission(PERMISSION_MANAGE_TENANTS)
    public void tenantConfig(WebContext ctx, String tenantId) {
        T tenant = find(getTenantClass(), tenantId);
        assertNotNew(tenant);
        ctx.respondWith().template("templates/biz/tenants/tenant-config.html.pasta", tenant);
    }

    /**
     * Provides an editor for setting additional and revoked permissions.
     *
     * @param ctx      the current request
     * @param tenantId the id of the tenant to change
     */
    @Routed("/tenant/:1/permissions")
    @LoginRequired
    @Permission(PERMISSION_MANAGE_TENANTS)
    public void tenantPermissionse(WebContext ctx, String tenantId) {
        T tenant = find(getTenantClass(), tenantId);
        assertNotNew(tenant);

        boolean handled = prepareSave(ctx).disableAutoload().withAfterSaveURI("/tenants").withPreSaveHandler(isNew -> {
            List<String> additionalPermissions = new ArrayList<>();
            List<String> revokedPermissions = new ArrayList<>();

            for (String permission : getPermissions()) {
                switch (ctx.get(permission).asString()) {
                    case "additional":
                        additionalPermissions.add(permission);
                        break;
                    case "revoked":
                        revokedPermissions.add(permission);
                        break;
                    default:
                }
            }

            tenant.getTenantData().getPackageData().getAdditionalPermissions().clear();
            tenant.getTenantData().getPackageData().getAdditionalPermissions().addAll(additionalPermissions);

            tenant.getTenantData().getPackageData().getRevokedPermissions().clear();
            tenant.getTenantData().getPackageData().getRevokedPermissions().addAll(revokedPermissions);
        }).saveEntity(tenant);

        if (!handled) {
            ctx.respondWith().template("templates/biz/tenants/tenant-permissions.html.pasta", tenant, this, packages);
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
        ph.withContext(ctx);

        ctx.respondWith()
           .template("templates/biz/tenants/select-tenant.html.pasta", ph.asPage(), isCurrentlySpying(ctx));
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
     *
     * @param ctx the current request
     * @param id  the id of the tenant to switch to
     */
    @LoginRequired
    @Routed("/tenants/select/:1")
    public void selectTenant(final WebContext ctx, String id) {
        if ("main".equals(id) || Strings.areEqual(determineOriginalTenantId(ctx), id)) {
            auditLog.neutral("AuditLog.switchedToMainTenant").causedByCurrentUser().forCurrentUser().log();

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

        auditLog.neutral("AuditLog.selectedTenant").causedByCurrentUser().forCurrentUser().log();

        ctx.setSessionValue(UserContext.getCurrentScope().getScopeId() + TenantUserManager.TENANT_SPY_ID_SUFFIX,
                            tenant.get().getIdAsString());
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
            ph.withContext(ctx);

            ph.asPage().getItems().forEach(tenant -> {
                result.accept(new AutocompleteHelper.Completion(tenant.getIdAsString(),
                                                                tenant.toString(),
                                                                tenant.toString()));
            });
        });
    }
}
