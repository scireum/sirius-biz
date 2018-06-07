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
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.web.controller.Controller;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

/**
 * Provides functionality to modify accounts.
 */
@Register(classes = Controller.class)
public class ProfileController extends BizController {

    private static final String PARAM_OLD_PASSWORD = "oldPassword";
    private static final String PARAM_NEW_PASSWORD = "newPassword";
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
                validateOldPassword(ctx, userAccount);
                
                String newPassword = ctx.get(PARAM_NEW_PASSWORD).asString();
                String confirmation = ctx.get(PARAM_CONFIRMATION).asString();

                userAccount.getLogin().verifyPassword(newPassword, confirmation, userAccount.getMinPasswordLength());
                userAccount.getLogin().setCleartextPassword(newPassword);
                oma.update(userAccount);
                showSavedMessage();
            } catch (Exception e) {
                UserContext.handle(e);
            }
        }

        ctx.respondWith().template("/templates/tenants/profile-change-password.html.pasta", userAccount);
    }

    private void validateOldPassword(WebContext ctx, UserAccount userAccount) {
        String oldPassword = ctx.get(PARAM_OLD_PASSWORD).asString();
        UserInfo userInfo = UserContext.get()
                                       .getUserManager()
                                       .findUserByCredentials(ctx, userAccount.getLogin().getUsername(), oldPassword);

        if (userInfo == null || userInfo.as(UserAccount.class).getId() != userAccount.getId()) {
            throw Exceptions.createHandled().withNLSKey("ProfileController.invalidOldPassword").handle();
        }
    }
}

