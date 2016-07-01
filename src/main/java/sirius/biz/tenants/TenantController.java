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
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Controller;
import sirius.web.controller.DefaultRoute;
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
 * Created by aha on 07.05.15.
 */
@Register(classes = Controller.class)
public class TenantController extends BizController {

    public static final String PERMISSION_MANAGE_TENANTS = "permission-manage-tenants";

    @ConfigValue("security.tenantPermissions")
    private List<String> permissions;

    public List<String> getPermissions() {
        return Collections.unmodifiableList(permissions);
    }

    public String getPermissionName(String role) {
        return NLS.get("TenantPermission." + role);
    }

    public String getPermissionDescription(String role) {
        return NLS.getIfExists("TenantPermission." + role + ".description", NLS.getCurrentLang()).orElse("");
    }

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
        ctx.respondWith().template("view/tenants/tenant-details.html", tenant, this);
    }

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

    @Routed("/tenant/:1/config")
    @LoginRequired
    @Permission(PERMISSION_MANAGE_TENANTS)
    public void tenantConfig(WebContext ctx, String tenantId) {
        Tenant tenant = find(Tenant.class, tenantId);
        assertNotNew(tenant);
        ctx.respondWith().template("view/tenants/tenant-config.html", tenant);
    }

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
}
