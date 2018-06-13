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
import sirius.db.mixing.Entity;
import sirius.db.mixing.SmartQuery;
import sirius.db.mixing.constraints.Like;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.controller.AutocompleteHelper;
import sirius.web.controller.Controller;
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Message;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.mails.Mails;
import sirius.web.security.LoginRequired;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;
import sirius.web.security.UserManager;
import sirius.web.services.JSONStructuredOutput;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Provides a GUI for managing user accounts.
 */
@Register(classes = Controller.class, framework = "biz.tenants")
public class UserAccountController extends BizController {

    /**
     * The permission required to add, modify or delete accounts
     */
    public static final String PERMISSION_MANAGE_USER_ACCOUNTS = "permission-manage-user-accounts";

    private static final String PARAM_PASSWORD = "password";
    private static final String PARAM_OLD_PASSWORD = "oldPassword";
    private static final String PARAM_NEW_PASSWORD = "newPassword";
    private static final String PARAM_CONFIRMATION = "confirmation";
    private static final String PARAM_NAME = "name";
    private static final String PARAM_USERNAME = "username";
    private static final String PARAM_URL = "url";
    private static final String PARAM_ROOT = "root";
    private static final String PARAM_EMAIL = "email";
    private static final String PARAM_REASON = "reason";

    @Part
    private Mails mails;

    @ConfigValue("product.wondergemRoot")
    private String wondergemRoot;

    @ConfigValue("security.roles")
    private List<String> roles;

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

        ctx.respondWith().template("templates/tenants/user-accounts.html.pasta", ph.asPage());
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

        boolean requestHandled = prepareSave(ctx).withAfterCreateURI("/user-account/${id}")
                                                 .withAfterSaveURI("/user-accounts")
                                                 .withPreSaveHandler(isNew -> {
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
            ctx.respondWith().template("templates/tenants/user-account-details.html.pasta", userAccount, this);
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
        ctx.respondWith().template("templates/tenants/user-account-config.html.pasta", userAccount);
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
                String oldPassword = ctx.get(PARAM_OLD_PASSWORD).asString();
                String newPassword = ctx.get(PARAM_NEW_PASSWORD).asString();
                String confirmation = ctx.get(PARAM_CONFIRMATION).asString();

                validateOldPassword(oldPassword, userAccount);
                userAccount.getLogin().verifyPassword(newPassword, confirmation, userAccount.getMinPasswordLength());
                userAccount.getLogin().setCleartextPassword(newPassword);
                oma.update(userAccount);
                showSavedMessage();
                accounts(ctx);
                return;
            } catch (Exception e) {
                UserContext.handle(e);
            }
        }
        ctx.respondWith().template("templates/tenants/user-account-password.html.pasta", userAccount);
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
              && ((TenantUserManager) userManager).validatePassword(userAccount, oldPassword))) {
            throw Exceptions.createHandled().withNLSKey("UserAccount.invalidOldPassword").handle();
        }
    }

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

        UserContext.message(Message.info(NLS.fmtr("UserAccountConroller.passwordGenerated")
                                            .set(PARAM_EMAIL, userAccount.getEmail())
                                            .format()));

        if (Strings.isFilled(userAccount.getEmail())) {
            Context context = Context.create()
                                     .set(PARAM_PASSWORD, userAccount.getLogin().getGeneratedPassword())
                                     .set(PARAM_NAME, userAccount.getPerson().getAddressableName())
                                     .set(PARAM_USERNAME, userAccount.getLogin().getUsername())
                                     .set(PARAM_URL, getBaseUrl())
                                     .set(PARAM_ROOT, wondergemRoot);
            mails.createEmail()
                 .to(userAccount.getEmail(), userAccount.getPerson().toString())
                 .subject(NLS.get("mail-password.subject"))
                 .textTemplate("mail/useraccount/password.pasta", context)
                 .htmlTemplate("mail/useraccount/password.html.pasta", context)
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
        List<UserAccount> accounts = oma.select(UserAccount.class)
                                        .eq(UserAccount.EMAIL, ctx.get(PARAM_EMAIL).asString())
                                        .limit(2)
                                        .queryList();
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
            Context context = Context.create()
                                     .set(PARAM_REASON,
                                          NLS.fmtr("UserAccountController.forgotPassword.reason")
                                             .set("ip", ctx.getRemoteIP().toString())
                                             .format())
                                     .set(PARAM_PASSWORD, account.getLogin().getGeneratedPassword())
                                     .set(PARAM_NAME, account.getPerson().getAddressableName())
                                     .set(PARAM_USERNAME, account.getLogin().getUsername())
                                     .set(PARAM_URL, getBaseUrl())
                                     .set(PARAM_ROOT, wondergemRoot);
            mails.createEmail()
                 .to(account.getEmail(), account.getPerson().toString())
                 .subject(NLS.get("mail-password.subject"))
                 .textTemplate("mail/useraccount/password.pasta", context)
                 .htmlTemplate("mail/useraccount/password.html.pasta", context)
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

    /**
     * Executes a logout for the current scope.
     *
     * @param ctx the current request
     */
    @Routed("/logout")
    public void logout(WebContext ctx) {
        UserContext.get().getUserManager().detachFromSession(getUser(), ctx);
        ctx.respondWith().redirectToGet(wondergemRoot);
    }

    /**
     * Autocompletion for UserAccounts.
     * <p>
     * Only accepts UserAccounts which belong to the current Tenant.
     *
     * @param ctx the current request
     */
    @LoginRequired
    @Routed("/user-accounts/autocomplete")
    public void usersAutocomplete(final WebContext ctx) {
        AutocompleteHelper.handle(ctx, (query, result) -> {
            oma.select(UserAccount.class)
               .eq(UserAccount.TENANT, currentTenant().getId())
               .where(Like.allWordsInAnyField(query,
                                              UserAccount.EMAIL,
                                              UserAccount.LOGIN.inner(LoginData.USERNAME),
                                              UserAccount.PERSON.inner(PersonData.FIRSTNAME),
                                              UserAccount.PERSON.inner(PersonData.LASTNAME)))
               .limit(10)

               .iterateAll(userAccount -> {
                   result.accept(new AutocompleteHelper.Completion(userAccount.getIdAsString(),
                                                                   userAccount.toString(),
                                                                   userAccount.toString()));
               });
        });
    }

    /**
     * Lists all users which the current user can "become" (switch to).
     *
     * @param ctx the current request
     */
    @Routed("/user-accounts/select")
    @LoginRequired
    @Permission(TenantUserManager.PERMISSION_SELECT_USER_ACCOUNT)
    public void selectUserAccounts(WebContext ctx) {
        SmartQuery<UserAccount> baseQuery = oma.select(UserAccount.class)
                                               .orderAsc(UserAccount.PERSON.inner(PersonData.LASTNAME))
                                               .orderAsc(UserAccount.PERSON.inner(PersonData.FIRSTNAME));
        if (!UserContext.getCurrentUser().hasPermission(TenantUserManager.PERMISSION_SYSTEM_TENANT)) {
            baseQuery.eq(UserAccount.TENANT, currentTenant());
        }

        baseQuery.fields(Entity.ID,
                         UserAccount.PERSON.inner(PersonData.LASTNAME),
                         UserAccount.PERSON.inner(PersonData.FIRSTNAME),
                         UserAccount.LOGIN.inner(LoginData.USERNAME),
                         UserAccount.TENANT.join(Tenant.NAME),
                         UserAccount.TENANT.join(Tenant.ACCOUNT_NUMBER));

        PageHelper<UserAccount> ph = PageHelper.withQuery(baseQuery);
        ph.withContext(ctx);
        ph.withSearchFields(UserAccount.PERSON.inner(PersonData.LASTNAME),
                            UserAccount.PERSON.inner(PersonData.FIRSTNAME),
                            UserAccount.LOGIN.inner(LoginData.USERNAME),
                            UserAccount.EMAIL,
                            UserAccount.TENANT.join(Tenant.NAME),
                            UserAccount.TENANT.join(Tenant.ACCOUNT_NUMBER));

        ctx.respondWith()
           .template("templates/tenants/select-user-account.html.pasta", ph.asPage(), isCurrentlySpying(ctx));
    }

    private boolean isCurrentlySpying(WebContext ctx) {
        return ctx.getSessionValue(UserContext.getCurrentScope().getScopeId() + TenantUserManager.SPY_ID_SUFFIX)
                  .isFilled();
    }

    /**
     * Makes the current user belong to the given tenant.
     *
     * @param ctx the current request
     * @param id  the id of the tenant to switch to
     */
    @LoginRequired
    @Routed("/user-accounts/select/:1")
    public void selectUserAccount(final WebContext ctx, String id) {
        if ("main".equals(id)) {
            ctx.setSessionValue(UserContext.getCurrentScope().getScopeId() + TenantUserManager.SPY_ID_SUFFIX, null);
            ctx.respondWith().redirectTemporarily("/user-accounts/select");
            return;
        }

        assertPermission(TenantUserManager.PERMISSION_SELECT_USER_ACCOUNT);

        UserAccount user = oma.find(UserAccount.class, id).orElse(null);
        if (user == null) {
            UserContext.get().addMessage(Message.error(NLS.get("UserAccountController.cannotBecomeUser")));
            selectUserAccounts(ctx);
        } else {
            if (!UserContext.getCurrentUser().hasPermission(TenantUserManager.PERMISSION_SYSTEM_TENANT)) {
                assertTenant(user);
            }

            ctx.setSessionValue(UserContext.getCurrentScope().getScopeId() + TenantUserManager.SPY_ID_SUFFIX,
                                user.getUniqueName());
            ctx.respondWith().redirectTemporarily(ctx.get("goto").asString(wondergemRoot));
        }
    }
}
