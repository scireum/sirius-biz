/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jdbc.tenants;

import sirius.biz.web.BizController;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.web.controller.Controller;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.security.UserContext;
import sirius.web.security.UserManager;

/**
 * Provides functionality to modify accounts.
 */
@Register(classes = Controller.class,framework = Tenants.FRAMEWORK_TENANTS)
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
        UserAccount userAccount = oma.refreshOrFail(getUser().getUserObject(UserAccount.class));
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
        UserAccount userAccount = oma.refreshOrFail(getUser().getUserObject(UserAccount.class));
        assertNotNew(userAccount);

        if (ctx.ensureSafePOST()) {
            try {
                String oldPassword = ctx.get(PARAM_OLD_PASSWORD).asString();
                String newPassword = ctx.get(PARAM_NEW_PASSWORD).asString();
                String confirmation = ctx.get(PARAM_CONFIRMATION).asString();

                validateOldPassword(oldPassword, userAccount);
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

    /**
     * Validates the old password for the given {@link UserAccount}.
     *
     * @param oldPassword the current request to read the old password from
     * @param userAccount the user account to validate the old password for
     * @throws sirius.kernel.health.HandledException if the old password is invalid
     */
    private void validateOldPassword(String oldPassword, UserAccount userAccount) {
        UserManager userManager = UserContext.get().getUserManager();

        if (!(userManager instanceof TenantUserManager
              && ((TenantUserManager) userManager).checkPassword(userAccount, oldPassword))) {
            throw Exceptions.createHandled().withNLSKey("ProfileController.invalidOldPassword").handle();
        }
    }
}

