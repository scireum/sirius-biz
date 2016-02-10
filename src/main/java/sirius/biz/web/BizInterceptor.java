/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Controller;
import sirius.web.controller.Interceptor;
import sirius.web.http.WebContext;
import sirius.web.security.ScopeInfo;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import java.lang.reflect.Method;

/**
 * Created by aha on 08.05.15.
 */
@Register
public class BizInterceptor implements Interceptor {

    @Override
    public boolean before(WebContext ctx, Controller controller, Method method) throws Exception {
        return false;
    }

    @Override
    public boolean beforePermissionError(String permission, WebContext ctx, Controller controller, Method method)
            throws Exception {
        if (UserContext.getCurrentScope() != ScopeInfo.DEFAULT_SCOPE) {
            return false;
        }
        if (UserInfo.PERMISSION_LOGGED_IN.equals(permission)) {
            ctx.respondWith().template("view/biz/login.html", ctx.getRequest().getUri());
        } else {
            ctx.respondWith()
               .template("view/wondergem/error.html",
                         NLS.fmtr("BizInterceptor.missingPermission").set("permission", permission).format());
        }
        return true;
    }
}
