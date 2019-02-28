/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.protocol.AuditLog;
import sirius.biz.web.BizController;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.di.std.Part;
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
 *
 * @param <I> specifies the effective type of database IDs used by the concrete implementation
 * @param <T> specifies the effective entity type used to represent Tenants
 * @param <U> specifies the effective entity type used to represent UserAccounts
 */
@Register(classes = Controller.class, framework = Tenants.FRAMEWORK_TENANTS)
public class ProfileController<I, T extends BaseEntity<I> & Tenant<I>, U extends BaseEntity<I> & UserAccount<I, T>>
        extends BizController {

    private static final String PARAM_OLD_PASSWORD = "oldPassword";
    private static final String PARAM_NEW_PASSWORD = "newPassword";
    private static final String PARAM_CONFIRMATION = "confirmation";

    @Part
    private AuditLog auditLog;

    /**
     * Shows a page where an account can change the user informations, e.g. mail, name, ...
     *
     * @param ctx the current request
     */
    @LoginRequired
    @Routed("/profile")
    public void profile(WebContext ctx) {
        U userAccount = getUser().getUserObject(getUserClass());
        userAccount = userAccount.getMapper().refreshOrFail(userAccount);
        assertNotNew(userAccount);

        boolean requestHandled = prepareSave(ctx).saveEntity(userAccount);

        if (!requestHandled) {
            ctx.respondWith().template("/templates/biz/tenants/profile.html.pasta", userAccount);
        }
    }

    @SuppressWarnings("unchecked")
    protected Class<U> getUserClass() {
        return (Class<U>) tenants.getUserClass();
    }

    /**
     * Shows a page where an account can change the password.
     *
     * @param ctx the current request
     */
    @LoginRequired
    @Routed("/profile/password")
    public void profileChangePassword(WebContext ctx) {
        U userAccount = getUser().getUserObject(getUserClass());
        userAccount = userAccount.getMapper().refreshOrFail(userAccount);
        assertNotNew(userAccount);

        if (ctx.ensureSafePOST()) {
            try {
                String oldPassword = ctx.get(PARAM_OLD_PASSWORD).asString();
                String newPassword = ctx.get(PARAM_NEW_PASSWORD).asString();
                String confirmation = ctx.get(PARAM_CONFIRMATION).asString();

                validateOldPassword(oldPassword, userAccount);
                userAccount.getUserAccountData()
                           .getLogin()
                           .verifyPassword(newPassword,
                                           confirmation,
                                           userAccount.getUserAccountData().getMinPasswordLength());
                userAccount.getUserAccountData().getLogin().setCleartextPassword(newPassword);
                userAccount.getMapper().update(userAccount);

                auditLog.neutral("AuditLog.passwordChange").causedByCurrentUser().forCurrentUser().log();

                showSavedMessage();

                ctx.respondWith().redirectToGet("/profile");
                return;
            } catch (Exception e) {
                auditLog.neutral("AuditLog.passwordChangeFailed").causedByCurrentUser().forCurrentUser().log();

                UserContext.handle(e);
            }
        }

        ctx.respondWith().template("/templates/biz/tenants/profile-change-password.html.pasta", userAccount);
    }

    /**
     * Validates the old password for the given {@link UserAccount}.
     *
     * @param oldPassword the current request to read the old password from
     * @param userAccount the user account to validate the old password for
     * @throws sirius.kernel.health.HandledException if the old password is invalid
     */
    @SuppressWarnings("unchecked")
    private void validateOldPassword(String oldPassword, U userAccount) {
        UserManager userManager = UserContext.get().getUserManager();

        if (!(userManager instanceof TenantUserManager && ((TenantUserManager<I, T, U>) userManager).checkPassword(
                userAccount,
                oldPassword))) {
            throw Exceptions.createHandled().withNLSKey("ProfileController.invalidOldPassword").handle();
        }
    }
}

