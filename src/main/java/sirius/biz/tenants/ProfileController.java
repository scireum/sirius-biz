/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.web.BizController;
import sirius.db.mixing.Schema;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.web.controller.Controller;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.security.UserContext;

/**
 * Provides functionality to modify accounts.
 */
@Register(classes = Controller.class)
public class ProfileController extends BizController {

    private static final String PARAM_PASSWORD = "password";
    private static final String PARAM_CONFIRMATION = "confirmation";

    /**
     * Shows a page where an account can change the user informations, e.g. mail, name, ...
     *
     * @param ctx the current request
     */
    @LoginRequired
    @Routed("/profile")
    public void profile(WebContext ctx) {
        UserAccount userAccount =
                find(UserAccount.class, Schema.splitUniqueName(UserContext.getCurrentUser().getUserId()).getSecond());
        assertNotNew(userAccount);

        boolean requestHandled = prepareSave(ctx).saveEntity(userAccount);

        if (!requestHandled) {
            ctx.respondWith().template("/templates/tenants/profile.html.pasta", userAccount);
        }
    }

    /**
     * Shows a page where an account can change the password.
     *
     * @param ctx the current request
     */
    @LoginRequired
    @Routed("/profile/password")
    public void profileChangePassword(WebContext ctx) {
        UserAccount userAccount =
                find(UserAccount.class, Schema.splitUniqueName(UserContext.getCurrentUser().getUserId()).getSecond());
        assertNotNew(userAccount);

        if (ctx.isPOST()) {
            try {
                String password = ctx.get(PARAM_PASSWORD).asString();
                String confirmation = ctx.get(PARAM_CONFIRMATION).asString();
                if (Strings.isEmpty(password) || password.length() < userAccount.getMinPasswordLength()) {
                    UserContext.setFieldError(PARAM_PASSWORD, null);
                    throw Exceptions.createHandled()
                                    .withNLSKey("Model.password.minLengthError")
                                    .set("minChars", userAccount.getMinPasswordLength())
                                    .handle();
                }

                if (!Strings.areEqual(password, confirmation)) {
                    UserContext.setFieldError(PARAM_CONFIRMATION, null);
                    throw Exceptions.createHandled().withNLSKey("Model.password.confirmationMismatch").handle();
                }

                userAccount.getLogin().setCleartextPassword(password);
                oma.update(userAccount);
                showSavedMessage();
                ctx.respondWith().template("/templates/tenants/profile.html.pasta", userAccount);
                return;
            } catch (Exception e) {
                UserContext.handle(e);
            }
        }

        ctx.respondWith().template("/templates/tenants/profile-change-password.html.pasta", userAccount);
    }
}

