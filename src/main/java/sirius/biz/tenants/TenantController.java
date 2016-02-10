/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.model.AddressData;
import sirius.biz.web.BizController;
import sirius.biz.web.PageHelper;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Controller;
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;

/**
 * Created by aha on 07.05.15.
 */
@Register(classes = Controller.class)
public class TenantController extends BizController {

    public static final String PERMISSION_MANAGE_TENANTS = "permission-manage-tenants";

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
        ctx.respondWith().template("view/tenants/tenant.html", tenant);
    }
}
