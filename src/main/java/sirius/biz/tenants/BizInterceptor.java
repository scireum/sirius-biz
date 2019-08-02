/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.kernel.async.CallContext;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Controller;
import sirius.web.controller.Interceptor;
import sirius.web.http.WebContext;
import sirius.web.security.ScopeInfo;
import sirius.web.security.UserContext;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Redirects unauthorized calls to the wondergem login page (for the default scope).
 */
@Register(framework = Tenants.FRAMEWORK_TENANTS)
public class BizInterceptor implements Interceptor {

    @ConfigValue("security.roles")
    protected List<String> roles;

    @ConfigValue("security.tenantPermissions")
    protected List<String> tenantPermissions;

    @Override
    public boolean before(WebContext ctx, boolean jsonCall, Controller controller, Method method) throws Exception {
        return false;
    }

    @Override
    public boolean beforePermissionError(String permission,
                                         WebContext ctx,
                                         boolean jsonCall,
                                         Controller controller,
                                         Method method) throws Exception {
        if (!ScopeInfo.DEFAULT_SCOPE.equals(UserContext.getCurrentScope())) {
            return false;
        }
        if (jsonCall) {
            return false;
        }
        if (!UserContext.getCurrentUser().isLoggedIn()) {
            ctx.respondWith().template("/templates/biz/login.html.pasta", ctx.getRequest().uri());
        } else {
            ctx.respondWith()
               .template("/templates/wondergem/error.html.pasta",
                         NLS.fmtr("BizInterceptor.missingPermission")
                            .set("permission", getTranslatedPermission(permission))
                            .format());
        }
        return true;
    }

    private String getTranslatedPermission(String permission) {
        if (roles.contains(permission)) {
            return NLS.get("Role." + permission);
        }
        if (tenantPermissions.contains(permission)) {
            return NLS.get("TenantPermission." + permission);
        }
        return NLS.getIfExists("Permission." + permission, CallContext.getCurrent().getLang()).orElse(permission);
    }

    @Override
    public boolean shouldExecuteRoute(WebContext ctx, boolean jsonCall, Controller controller) {
        return true;
    }
}
