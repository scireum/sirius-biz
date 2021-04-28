/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Controller;
import sirius.web.controller.Interceptor;
import sirius.web.http.WebContext;
import sirius.web.security.Permissions;
import sirius.web.security.ScopeInfo;
import sirius.web.security.UserContext;

import java.lang.reflect.Method;

/**
 * Redirects unauthorized calls to the wondergem login page (for the default scope).
 */
@Register(framework = Tenants.FRAMEWORK_TENANTS)
public class BizInterceptor implements Interceptor {

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
               .template("/templates/tycho/error.html.pasta",
                         NLS.fmtr("BizInterceptor.missingPermission")
                            .set("permission", Permissions.getTranslatedPermission(permission))
                            .format());
        }
        return true;
    }

    @Override
    public boolean shouldExecuteRoute(WebContext ctx, boolean jsonCall, Controller controller) {
        return true;
    }
}
