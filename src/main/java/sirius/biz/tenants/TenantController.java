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
import sirius.biz.web.BizController;
import sirius.biz.web.PageHelper;
import sirius.db.mixing.SmartQuery;
import sirius.db.mixing.constraints.And;
import sirius.db.mixing.constraints.FieldOperator;
import sirius.db.mixing.constraints.Or;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Controller;
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
@Register(classes = Controller.class)
public class TenantController extends BizController {

    /**
     * Contains the permission required to manage tenants.
     */
    public static final String PERMISSION_MANAGE_TENANTS = "permission-manage-tenants";

    /**
     * Contains the permission required to switch the tenant.
     */
    public static final String PERMISSION_SELECT_TENANT = "permission-select-tenant";

    /**
     * Contains a list of all available features or permission which can be granted to a tenant.
     */
    @ConfigValue("security.tenantPermissions")
    private List<String> permissions;

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
        return NLS.getIfExists("TenantPermission." + role + ".description", NLS.getCurrentLang()).orElse("");
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
        PageHelper<Tenant> ph = PageHelper.withQuery(oma.select(Tenant.class).orderAsc(Tenant.NAME));
        ph.withContext(ctx);
        ph.withSearchFields(Tenant.NAME,
                            Tenant.ACCOUNT_NUMBER,
                            Tenant.ADDRESS.inner(AddressData.STREET),
                            Tenant.ADDRESS.inner(AddressData.CITY));

        ctx.respondWith().template("view/tenants/tenants.html", ph.asPage());
    }

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
        Tenant tenant = find(Tenant.class, tenantId);
        if (ctx.isPOST()) {
            try {
                boolean wasNew = tenant.isNew();
                load(ctx, tenant);

                tenant.getPermissions().getPermissions().clear();
                for (String permission : ctx.getParameters("permissions")) {
                    // Ensure that only real permissions end up in the permissions list,
                    // as roles, permissions and flags later end up in the same vector
                    // therefore we don't want nothing else but tenant permissions in this list
                    if (getPermissions().contains(permission)) {
                        tenant.getPermissions().getPermissions().add(permission);
                    }
                }

                oma.update(tenant);
                showSavedMessage();
                if (wasNew) {
                    ctx.respondWith().redirectTemporarily(WebContext.getContextPrefix() + "/tenant/" + tenant.getId());
                    return;
                }
            } catch (Throwable e) {
                UserContext.handle(e);
            }
        }
        ctx.respondWith()
           .template("view/tenants/tenant-details.html",
                     tenant,
                     this,
                     oma.select(Tenant.class)
                        .orderAsc(Tenant.NAME)
                        .where(FieldOperator.on(Tenant.ID).notEqual(tenant.getId()))
                        .queryList());
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
        Tenant tenant = find(Tenant.class, tenantId);
        assertNotNew(tenant);
        load(ctx, tenant);
        if (ctx.hasParameter(Tenant.PERMISSIONS.inner(PermissionData.CONFIG_STRING).getName())) {
            tenant.getPermissions().getConfig();
        }
        oma.update(tenant);
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
        Tenant tenant = find(Tenant.class, tenantId);
        assertNotNew(tenant);
        ctx.respondWith().template("view/tenants/tenant-config.html", tenant);
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
        Optional<Tenant> t = oma.find(Tenant.class, tenantId);
        if (t.isPresent()) {
            if (t.get().getId() == currentTenant().getId()) {
                throw Exceptions.createHandled().withNLSKey("TenantController.cannotDeleteSelf").handle();
            }
            oma.delete(t.get());
            showDeletedMessage();
        }
        tenants(ctx);
    }

    /**
     * Lists all tenants which the current user can "become" (switch to).
     *
     * @param ctx the current request
     */
    @Routed("/tenants/select")
    @DefaultRoute
    @LoginRequired
    @Permission(PERMISSION_SELECT_TENANT)
    public void selectTenants(WebContext ctx) {
        SmartQuery<Tenant> baseQuery = queryPossibleTenants(ctx).orderAsc(Tenant.NAME);
        PageHelper<Tenant> ph = PageHelper.withQuery(baseQuery);
        ph.withContext(ctx);
        ph.withSearchFields(Tenant.NAME,
                            Tenant.ACCOUNT_NUMBER,
                            Tenant.ADDRESS.inner(AddressData.STREET),
                            Tenant.ADDRESS.inner(AddressData.CITY));

        ctx.respondWith().template("view/tenants/select-tenant.html", ph.asPage());
    }

    @ConfigValue("product.wondergemRoot")
    private String wondergemRoot;

    @LoginRequired
    @Routed("/tenants/select/:1")
    public void selectTenant(final WebContext ctx, String id) {
        if ("main".equals(id)) {
            ctx.setSessionValue(UserContext.getCurrentScope().getScopeId() + TenantUserManager.TENANT_SPY_ID_SUFFIX,
                                null);
            ctx.respondWith().redirectTemporarily("/tenants/select");
            return;
        }

        assertPermission(PERMISSION_SELECT_TENANT);

        SmartQuery<Tenant> baseQuery = queryPossibleTenants(ctx).eq(Tenant.ID, id);

        Tenant tenant = baseQuery.queryFirst();
        if (tenant == null) {
            UserContext.get().addMessage(Message.error(NLS.get("TenantController.cannotBecomeTenant")));
            selectTenants(ctx);
            return;
        }

        ctx.setSessionValue(UserContext.getCurrentScope().getScopeId() + TenantUserManager.TENANT_SPY_ID_SUFFIX,
                            tenant.getIdAsString());
        ctx.respondWith().redirectTemporarily(ctx.get("goto").asString(wondergemRoot));
    }

    private SmartQuery<Tenant> queryPossibleTenants(WebContext ctx) {
        String tenantId = ((TenantUserManager) UserContext.get().getUserManager()).getOriginalTenantId(ctx);
        Optional<Tenant> originalTenant = oma.find(Tenant.class, tenantId);
        if (originalTenant.isPresent()) {
            SmartQuery<Tenant> baseQuery = oma.select(Tenant.class);
            if (!hasPermission(TenantUserManager.PERMISSION_SYSTEM_TENANT)) {
                baseQuery.where(Or.of(And.of(FieldOperator.on(Tenant.PARENT).eq(tenantId),
                                             FieldOperator.on(Tenant.PARENT_CAN_ACCESS).eq(true)),
                                      And.of(FieldOperator.on(Tenant.ID).eq(originalTenant.get().getParent().getId()),
                                             FieldOperator.on(Tenant.CAN_ACCESS_PARENT).eq(true))));
            }
            return baseQuery;
        } else {
            throw Exceptions.createHandled().withSystemErrorMessage("Cannot determine current tenant!").handle();
        }
    }
}
