/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.model.LoginData;
import sirius.biz.model.PermissionData;
import sirius.biz.model.PersonData;
import sirius.biz.web.BizController;
import sirius.biz.web.PageHelper;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Controller;
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.mails.Mails;
import sirius.web.security.LoginRequired;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;
import sirius.web.services.JSONStructuredOutput;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Provides a GUI for managing user accounts.
 */
@Framework("tenants")
@Register(classes = Controller.class)
public class UserAccountController extends BizController {

    /**
     * The permission required to add, modify or delete accounts
     */
    public static final String PERMISSION_MANAGE_USER_ACCOUNTS = "permission-manage-user-accounts";

    /**
     * Shows a list of all available users of the current tenant.
     *
     * @param ctx the current request
     */
    @Routed("/user-accounts")
    @DefaultRoute
    @LoginRequired
    @Permission(PERMISSION_MANAGE_USER_ACCOUNTS)
    public void accounts(WebContext ctx) {
        PageHelper<UserAccount> ph = PageHelper.withQuery(oma.select(UserAccount.class)
                                                             .orderAsc(UserAccount.PERSON.inner(PersonData.LASTNAME))
                                                             .orderAsc(UserAccount.PERSON.inner(PersonData.FIRSTNAME)));
        ph.forCurrentTenant();
        ph.withContext(ctx);
        ph.withSearchFields(UserAccount.EMAIL,
                            UserAccount.LOGIN.inner(LoginData.USERNAME),
                            UserAccount.PERSON.inner(PersonData.FIRSTNAME),
                            UserAccount.PERSON.inner(PersonData.LASTNAME));

        ctx.respondWith().template("view/tenants/user-accounts.html", ph.asPage());
    }

    /**
     * Shows an editor for the given account.
     *
     * @param ctx       the current request
     * @param accountId the {@link UserAccount} to edit
     */
    @Routed("/user-account/:1")
    @LoginRequired
    @Permission(PERMISSION_MANAGE_USER_ACCOUNTS)
    public void account(WebContext ctx, String accountId) {
        UserAccount userAccount = findForTenant(UserAccount.class, accountId);

        boolean requestHandled = prepareSave(ctx).editAfterCreate()
                                                 .withAfterCreateURI("/user-account/${id}")
                                                 .withAfterSaveURI("/user-accounts")
                                                 .withPreSaveHandler((isNew) -> {
                                                     userAccount.getPermissions().getPermissions().clear();
                                                     for (String role : ctx.getParameters("roles")) {
                                                         // Ensure that only real roles end up in the permissions list,
                                                         // as roles, permissions and flags later end up in the same vector
                                                         // therefore we don't want nothing else but user roles in this list
                                                         if (getRoles().contains(role)) {
                                                             userAccount.getPermissions().getPermissions().add(role);
                                                         }
                                                     }
                                                 })
                                                 .saveEntity(userAccount);

        if (!requestHandled) {
            validate(userAccount);
            ctx.respondWith().template("view/tenants/user-account-details.html", userAccount, this);
        }
    }

    /**
     * Shows an editor for the custom configuration of the given user.
     *
     * @param ctx       the current request
     * @param accountId the id of the account which config will be edited
     */
    @Routed("/user-account/:1/config")
    @LoginRequired
    @Permission(PERMISSION_MANAGE_USER_ACCOUNTS)
    public void accountConfig(WebContext ctx, String accountId) {
        UserAccount userAccount = findForTenant(UserAccount.class, accountId);
        assertNotNew(userAccount);
        ctx.respondWith().template("view/tenants/user-account-config.html", userAccount);
    }

    /**
     * Provides a JSON API to change the settings of an account, including its configuration.
     *
     * @param ctx       the current request
     * @param out       the JSON response being generated
     * @param accountId the id of the account to update
     */
    @Routed(value = "/user-account/:1/update", jsonCall = true)
    @LoginRequired
    @Permission(PERMISSION_MANAGE_USER_ACCOUNTS)
    public void accountUpdate(WebContext ctx, JSONStructuredOutput out, String accountId) {
        UserAccount userAccount = findForTenant(UserAccount.class, accountId);
        assertNotNew(userAccount);
        load(ctx, userAccount);
        if (ctx.hasParameter(UserAccount.PERMISSIONS.inner(PermissionData.CONFIG_STRING).getName())) {
            userAccount.getPermissions().getConfig();
        }
        oma.update(userAccount);
    }

    @ConfigValue("security.roles")
    private List<String> roles;

    /**
     * Lists all roles which can be granted to a user.
     *
     * @return all roles which can be granted to a user
     */
    public List<String> getRoles() {
        return Collections.unmodifiableList(roles);
    }

    /**
     * Returns the translated name of a role.
     *
     * @param role the role to translate
     * @return a translated name for the given role
     */
    public String getRoleName(String role) {
        return NLS.get("Role." + role);
    }

    /**
     * Returns a description of the role.
     *
     * @param role the role to fetch the description for
     * @return the description of the given role
     */
    public String getRoleDescription(String role) {
        return NLS.get("Role." + role + ".description");
    }

    /**
     * Shows an editor to change the password of an account.
     *
     * @param ctx the current request
     * @param id  the account to change the password for
     */
    @Routed("/user-account/:1/password")
    @LoginRequired
    @Permission(PERMISSION_MANAGE_USER_ACCOUNTS)
    public void password(final WebContext ctx, String id) {
        UserAccount userAccount = findForTenant(UserAccount.class, id);
        assertNotNew(userAccount);

        if (ctx.isPOST()) {
            try {
                String password = ctx.get("password").asString();
                String confirmation = ctx.get("confirmation").asString();
                if (Strings.isEmpty(password) || password.length() < userAccount.getMinPasswordLength()) {
                    UserContext.setFieldError("password", null);
                    throw Exceptions.createHandled()
                                    .withNLSKey("Model.password.minLengthError")
                                    .set("minChars", userAccount.getMinPasswordLength())
                                    .handle();
                }
                if (!Strings.areEqual(password, confirmation)) {
                    UserContext.setFieldError("confirmation", null);
                    throw Exceptions.createHandled().withNLSKey("Model.password.confirmationMismatch").handle();
                }
                userAccount.getLogin().setCleartextPassword(password);
                oma.update(userAccount);
                showSavedMessage();
                accounts(ctx);
                return;
            } catch (Throwable e) {
                UserContext.handle(e);
            }
        }
        ctx.respondWith().template("view/tenants/user-account-password.html", userAccount);
    }

    @Part
    private Mails mails;

    /**
     * Generates a new password for the given account and send a mail to the user.
     *
     * @param ctx the current request
     * @param id  the account for which a password is to be created
     */
    @Routed("/user-account/:1/generate-password")
    @LoginRequired
    @Permission(PERMISSION_MANAGE_USER_ACCOUNTS)
    public void generatePassword(final WebContext ctx, String id) {
        UserAccount userAccount = findForTenant(UserAccount.class, id);
        assertNotNew(userAccount);

        userAccount.getLogin().setGeneratedPassword(Strings.generatePassword());
        oma.update(userAccount);
        showSavedMessage();
        if (Strings.isFilled(userAccount.getEmail())) {
            mails.createEmail()
                 .useMailTemplate("user-account-password",
                                  Context.create()
                                         .set("password", userAccount.getLogin().getGeneratedPassword())
                                         .set("name", userAccount.getPerson().getAddressableName())
                                         .set("username", userAccount.getLogin().getUsername())
                                         .set("url", getBaseUrl()))
                 .to(userAccount.getEmail(), userAccount.getPerson().toString())
                 .send();
        }

        accounts(ctx);
    }

    /**
     * Provides a JSON API which re-sends the password to the account with the given email address.
     *
     * @param ctx the current request
     * @param out the JSON response being generated
     */
    @Routed(value = "/forgotPassword", jsonCall = true)
    public void forgotPassword(final WebContext ctx, JSONStructuredOutput out) {
        List<UserAccount> accounts =
                oma.select(UserAccount.class).eq(UserAccount.EMAIL, ctx.get("email").asString()).limit(2).queryList();
        if (accounts.isEmpty()) {
            throw Exceptions.createHandled().withNLSKey("UserAccountController.noUserFoundForEmail").handle();
        }

        if (accounts.size() > 1) {
            throw Exceptions.createHandled().withNLSKey("UserAccountController.tooManyUsersFoundForEmail").handle();
        }

        UserAccount account = accounts.get(0);
        if (account.getLogin().isAccountLocked()) {
            throw Exceptions.createHandled().withNLSKey("LoginData.accountIsLocked").handle();
        }
        account.getLogin().setGeneratedPassword(Strings.generatePassword());
        oma.update(account);

        if (Strings.isFilled(account.getEmail())) {
            mails.createEmail()
                 .useMailTemplate("user-account-password",
                                  Context.create()
                                         .set("reason",
                                              NLS.fmtr("UserAccountController.forgotPassword.reason")
                                                 .set("ip", ctx.getRemoteIP().toString())
                                                 .format())
                                         .set("password", account.getLogin().getGeneratedPassword())
                                         .set("name", account.getPerson().getAddressableName())
                                         .set("username", account.getLogin().getUsername())
                                         .set("url", getBaseUrl()))
                 .to(account.getEmail(), account.getPerson().toString())
                 .send();
        }
    }

    /**
     * Deletes the given account.
     *
     * @param ctx the current request
     * @param id  the account to delete
     */
    @LoginRequired
    @Routed("/user-account/:1/delete")
    @Permission(PERMISSION_MANAGE_USER_ACCOUNTS)
    public void deleteAdmin(final WebContext ctx, String id) {
        Optional<UserAccount> account = tryFindForTenant(UserAccount.class, id);
        if (account.isPresent()) {
            oma.delete(account.get());
            showDeletedMessage();
        }
        accounts(ctx);
    }

    @ConfigValue("product.wondergemRoot")
    private String wondergemRoot;

    /**
     * Executes a logout for the current scope.
     *
     * @param ctx the current request
     */
    @Routed("/logout")
    public void logout(WebContext ctx) {
        UserContext.get().getUserManager().detachFromSession(getUser(), ctx);
        ctx.respondWith().redirectTemporarily(wondergemRoot);
    }
}
