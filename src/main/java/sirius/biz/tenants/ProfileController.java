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
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.security.UserContext;
import sirius.web.security.UserManager;

import java.io.Serializable;

/**
 * Provides functionality to modify accounts.
 *
 * @param <I> specifies the effective type of database IDs used by the concrete implementation
 * @param <T> specifies the effective entity type used to represent Tenants
 * @param <U> specifies the effective entity type used to represent UserAccounts
 */
@Register(framework = Tenants.FRAMEWORK_TENANTS)
public class ProfileController<I extends Serializable, T extends BaseEntity<I> & Tenant<I>, U extends BaseEntity<I> & UserAccount<I, T>>
        extends BizController {

    private static final String PARAM_OLD_PASSWORD = "oldPassword";
    private static final String PARAM_NEW_PASSWORD = "newPassword";
    private static final String PARAM_CONFIRMATION = "confirmation";

    @Part
    private AuditLog auditLog;

    @ConfigValue("product.wondergemRoot")
    protected String wondergemRoot;

    @Part
    private UserAccountController<?, ?, ?> userAccountController;

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
            ctx.respondWith().template("/templates/biz/tenants/profile.html.pasta", userAccount, userAccountController);
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
                validateNewPassword(userAccount, newPassword, confirmation);
                userAccount.getUserAccountData().getLogin().setCleartextPassword(newPassword);
                userAccount.getMapper().update(userAccount);

                auditLog.neutral("AuditLog.passwordChange").causedByCurrentUser().forCurrentUser().log();

                showSavedMessage();

                updateFingerprintInCurrentSession(ctx, userAccount);

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
     * Invalidates all client sessions of the current user by changing the fingerprint.
     *
     * @param ctx the current request
     */
    @LoginRequired
    @Routed("/profile/changeFingerprint")
    public void profileChangeFingerprint(WebContext ctx) {
        U userAccount = getUser().getUserObject(getUserClass());
        userAccount = userAccount.getMapper().refreshOrFail(userAccount);
        assertNotNew(userAccount);

        userAccount.getUserAccountData().getLogin().resetFingerprint();
        userAccount.getMapper().update(userAccount);

        ctx.respondWith().redirectToGet(wondergemRoot);
    }

    /**
     * Directly updates the fingerprint in the current session so that the user isn't logged out when updating the password.
     *
     * @param ctx         the current request
     * @param userAccount the account with the new fingerprint of the user
     */
    private void updateFingerprintInCurrentSession(WebContext ctx, U userAccount) {
        TenantUserManager<?, ?, ?> userManager = (TenantUserManager<?, ?, ?>) UserContext.get().getUserManager();
        userManager.installFingerprintInSession(ctx, userAccount.getUserAccountData().getLogin().getFingerprint());
    }

    /**
     * Validates that the given password matches the given confirmation and also that it is sufficiently long.
     *
     * @param userAccount  the user being updated
     * @param newPassword  the new password to set
     * @param confirmation the confirmation given by the user
     */
    private void validateNewPassword(U userAccount, String newPassword, String confirmation) {
        userAccount.getUserAccountData()
                   .getLogin()
                   .verifyPassword(newPassword, confirmation, userAccount.getUserAccountData().getMinPasswordLength());
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

