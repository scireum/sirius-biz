/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.model.AddressData;
import sirius.biz.model.PermissionData;
import sirius.biz.protocol.AuditLog;
import sirius.biz.web.BasePageHelper;
import sirius.biz.web.BizController;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.query.QueryField;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Message;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;
import sirius.web.services.JSONStructuredOutput;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Provides a GUI for managing tenants.
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
        ph.withSearchFields(QueryField.contains(Tenant.TENANT_DATA.inner(TenantData.NAME)),
                            QueryField.contains(Tenant.TENANT_DATA.inner(TenantData.ACCOUNT_NUMBER)),
                            QueryField.contains(Tenant.TENANT_DATA.inner(TenantData.ADDRESS).inner(AddressData.STREET)),
                            QueryField.contains(Tenant.TENANT_DATA.inner(TenantData.ADDRESS).inner(AddressData.CITY)));

        ctx.respondWith().template("templates/tenants/tenants.html.pasta", ph.asPage());
    }

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
            tenant.getTenantData().getPermissions().getPermissions().clear();
            for (String permission : ctx.getParameters("permissions")) {
                // Ensure that only real permissions end up in the permissions list,
                // as roles, permissions and flags later end up in the same vector
                // therefore we don't want nothing else but tenant permissions in this list
                if (getPermissions().contains(permission)) {
                    tenant.getTenantData().getPermissions().getPermissions().add(permission);
                }
            }
        });

        boolean requestHandled = saveHelper.saveEntity(tenant);
        if (!requestHandled) {
            validate(tenant);
            ctx.respondWith()
               .template("templates/tenants/tenant-details.html.pasta",
                         tenant,
                         this,
                         mixing.getDescriptor(getTenantClass())
                               .getMapper()
                               .select(getTenantClass())
                               .orderAsc(Tenant.TENANT_DATA.inner(TenantData.NAME))
                               .queryList());
        }
    }

    protected abstract Class<T> getTenantClass();

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
        if (ctx.hasParameter(Tenant.TENANT_DATA.inner(TenantData.PERMISSIONS)
                                               .inner(PermissionData.CONFIG_STRING)
                                               .getName())) {
            tenant.getTenantData().getPermissions().getConfig();
        }
        tenant.getTenantData().getPermissions().getPermissions().clear();
        for (String permission : ctx.getParameters("permissions")) {
            // Ensure that only real roles end up in the permissions list,
            // as roles, permissions and flags later end up in the same vector
            // therefore we don't want nothing else but tenant permissions in this list
            if (getPermissions().contains(permission)) {
                tenant.getTenantData().getPermissions().getPermissions().add(permission);
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
        ctx.respondWith().template("templates/tenants/tenant-config.html.pasta", tenant);
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
            tenant.getMapper().delete(tenant);
            showDeletedMessage();
        });

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
        ph.withSearchFields(QueryField.contains(Tenant.TENANT_DATA.inner(TenantData.NAME)),
                            QueryField.contains(Tenant.TENANT_DATA.inner(TenantData.ACCOUNT_NUMBER)),
                            QueryField.contains(Tenant.TENANT_DATA.inner(TenantData.ADDRESS).inner(AddressData.STREET)),
                            QueryField.contains(Tenant.TENANT_DATA.inner(TenantData.ADDRESS).inner(AddressData.CITY)));

        ctx.respondWith().template("templates/tenants/select-tenant.html.pasta", ph.asPage(), isCurrentlySpying(ctx));
    }

    private T determineCurrentTenant(WebContext ctx) {
        String tenantId = ((TenantUserManager<?, ?, ?>) UserContext.get().getUserManager()).getOriginalTenantId(ctx);
        return mixing.getDescriptor(getTenantClass())
                     .getMapper()
                     .find(getTenantClass(), tenantId)
                     .orElseThrow(() -> Exceptions.createHandled()
                                                  .withSystemErrorMessage("Cannot determine current tenant!")
                                                  .handle());
    }

    protected abstract BasePageHelper<T, ?, ?, ?> getSelectableTenantsAsPage(WebContext ctx, T currentTenant);
    //  SmartQuery<Tenant> baseQuery = queryPossibleTenants(ctx).orderAsc(Tenant.NAME);

    private boolean isCurrentlySpying(WebContext ctx) {
        return ctx.getSessionValue(UserContext.getCurrentScope().getScopeId() + TenantUserManager.TENANT_SPY_ID_SUFFIX)
                  .isFilled();
    }

    /**
     * Makes the current user belong to the given tenant.
     *
     * @param ctx the current request
     * @param id  the id of the tenant to switch to
     */
    @LoginRequired
    @Routed("/tenants/select/:1")
    public void selectTenant(final WebContext ctx, String id) {
        if ("main".equals(id)) {
            auditLog.neutral("AuditLog.switchedToMainTenant").causedByCurrentUser().forCurrentUser().log();

            ctx.setSessionValue(UserContext.getCurrentScope().getScopeId() + TenantUserManager.TENANT_SPY_ID_SUFFIX,
                                null);
            ctx.respondWith().redirectTemporarily("/tenants/select");
            return;
        }

        assertPermission(TenantUserManager.PERMISSION_SELECT_TENANT);

        Optional<T> tenant = tryToSelectTenant(id, determineCurrentTenant(ctx));
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

    protected abstract Optional<T> tryToSelectTenant(String id, T currentTenant);

    //    {
//        SmartQuery<T> baseQuery = queryPossibleTenants(ctx).eq(Tenant.ID, id);
//
//        return baseQuery.queryFirst();
//    }
//
//    private SmartQuery<Tenant> queryPossibleTenants(WebContext ctx) {
//        String tenantId = ((TenantUserManager<?, ?, ?>) UserContext.get().getUserManager()).getOriginalTenantId(ctx);
//        Optional<Tenant> originalTenant = oma.find(Tenant.class, tenantId);
//        if (originalTenant.isPresent()) {
//            SmartQuery<Tenant> baseQuery = oma.select(Tenant.class);
//            if (!hasPermission(TenantUserManager.PERMISSION_SYSTEM_TENANT)) {
//                if (originalTenant.get().isCanAccessParent()) {
//                    baseQuery.where(OMA.FILTERS.or(OMA.FILTERS.and(OMA.FILTERS.eq(Tenant.PARENT, tenantId),
//                                                                   OMA.FILTERS.eq(Tenant.PARENT_CAN_ACCESS, true)),
//                                                   OMA.FILTERS.eq(Tenant.ID,
//                                                                  originalTenant.get().getParent().getId())));
//                } else {
//                    baseQuery.where(OMA.FILTERS.and(OMA.FILTERS.eq(Tenant.PARENT, tenantId),
//                                                    OMA.FILTERS.eq(Tenant.PARENT_CAN_ACCESS, true)));
//                }
//            }
//            return baseQuery;
//        } else {
//            throw Exceptions.createHandled().withSystemErrorMessage("Cannot determine current tenant!").handle();
//        }
//    }
}
