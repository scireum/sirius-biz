/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.codelists.CodeLists;
import sirius.biz.web.BizController;
import sirius.db.mixing.Schema;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
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

    @Part
    private static CodeLists cls;

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
            ctx.respondWith()
               .template("/templates/profile/profile.html.pasta", userAccount, cls.getEntries("salutations"));
        }
    }

    /**
     * Shows a page where an account can change the password.
     *
     * @param ctx the current request
     */
    @LoginRequired
    @Routed("/profile/change-password")
    public void profileChangePassword(WebContext ctx) {
        UserAccount userAccount =
                find(UserAccount.class, Schema.splitUniqueName(UserContext.getCurrentUser().getUserId()).getSecond());
        assertNotNew(userAccount);

        boolean requestHandled = prepareSave(ctx).withPreSaveHandler(isNew -> {
            handlePasswordChange(ctx, userAccount);
        }).withAfterSaveURI("/profile").saveEntity(userAccount);

        if (!requestHandled) {
            ctx.respondWith().template("/templates/profile/profile-change-password.html.pasta", userAccount);
        }
    }

    private void handlePasswordChange(WebContext ctx, UserAccount userAccount) {
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
    }
}

