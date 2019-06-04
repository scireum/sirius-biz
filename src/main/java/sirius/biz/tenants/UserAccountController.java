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
import sirius.biz.protocol.AuditLog;
import sirius.biz.web.BasePageHelper;
import sirius.biz.web.BizController;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.controller.AutocompleteHelper;
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Message;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.mails.Mails;
import sirius.web.security.LoginRequired;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;
import sirius.web.services.JSONStructuredOutput;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Provides a GUI for managing user accounts.
 *
 * @param <I> the type of database IDs used by the concrete implementation
 * @param <T> specifies the effective entity type used to represent Tenants
 * @param <U> specifies the effective entity type used to represent UserAccounts
 */
public abstract class UserAccountController<I, T extends BaseEntity<I> & Tenant<I>, U extends BaseEntity<I> & UserAccount<I, T>>
        extends BizController {

    /**
     * The permission required to add, modify or delete accounts
     */
    public static final String PERMISSION_MANAGE_USER_ACCOUNTS = "permission-manage-user-accounts";

    private static final String PARAM_PASSWORD = "password";
    private static final String PARAM_NAME = "name";
    private static final String PARAM_USERNAME = "username";
    private static final String PARAM_URL = "url";
    private static final String PARAM_ROOT = "root";
    private static final String PARAM_EMAIL = "email";
    private static final String PARAM_REASON = "reason";

    @Part
    protected Mails mails;

    @ConfigValue("product.wondergemRoot")
    protected String wondergemRoot;

    @ConfigValue("security.roles")
    protected List<String> roles;

    @Part
    protected AuditLog auditLog;

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
        BasePageHelper<U, ?, ?, ?> ph = getUsersAsPage();
        ph.withContext(ctx);
        ph.addBooleanFacet(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                        .inner(LoginData.ACCOUNT_LOCKED)
                                                        .toString(), NLS.get("LoginData.accountLocked"));

        ctx.respondWith().template("templates/biz/tenants/user-accounts.html.pasta", ph.asPage(), getUserClass());
    }

    /**
     * Returns the effective entity class used to represent user accounts.
     *
     * @return the effective entity class for user accounts
     */
    @SuppressWarnings("unchecked")
    protected Class<U> getUserClass() {
        return (Class<U>) tenants.getUserClass();
    }

    /**
     * Constructs a page helper for the user accounts to view.
     *
     * @return the list of available user accounts wrapped as page helper
     */
    protected abstract BasePageHelper<U, ?, ?, ?> getUsersAsPage();

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
        U userAccount = findForTenant(getUserClass(), accountId);

        boolean requestHandled = prepareSave(ctx).withAfterCreateURI("/user-account/${id}")
                                                 .withAfterSaveURI("/user-accounts")
                                                 .withPreSaveHandler(isNew -> {
                                                     userAccount.getUserAccountData()
                                                                .getPermissions()
                                                                .getPermissions()
                                                                .clear();
                                                     for (String role : ctx.getParameters("roles")) {
                                                         // Ensure that only real roles end up in the permissions list,
                                                         // as roles, permissions and flags later end up in the same vector
                                                         // therefore we don't want nothing else but user roles in this list
                                                         if (getRoles().contains(role)) {
                                                             userAccount.getUserAccountData()
                                                                        .getPermissions()
                                                                        .getPermissions()
                                                                        .add(role);
                                                         }
                                                     }
                                                 })
                                                 .saveEntity(userAccount);

        if (!requestHandled) {
            validate(userAccount);
            List<String> availableLanguages =
                    ((TenantUserManager<?, ?, ?>) UserContext.get().getUserManager()).getAvailableLanguages();
            ctx.respondWith()
               .template("templates/biz/tenants/user-account-details.html.pasta",
                         userAccount,
                         this,
                         availableLanguages);
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
        U userAccount = findForTenant(getUserClass(), accountId);
        assertNotNew(userAccount);
        ctx.respondWith().template("templates/biz/tenants/user-account-config.html.pasta", userAccount);
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
        U userAccount = findForTenant(getUserClass(), accountId);
        assertNotNew(userAccount);
        load(ctx, userAccount);
        if (ctx.hasParameter(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERMISSIONS)
                                                          .inner(PermissionData.CONFIG_STRING)
                                                          .getName())) {
            userAccount.getUserAccountData().getPermissions().getConfig();
        }
        userAccount.getMapper().update(userAccount);
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
     * Generates a new password for the given account.
     *
     * @param ctx the current request
     * @param id  the account for which a password is to be created
     */
    @Routed("/user-account/:1/generate-password")
    @LoginRequired
    @Permission(PERMISSION_MANAGE_USER_ACCOUNTS)
    public void generatePassword(final WebContext ctx, String id) {
        U userAccount = findForTenant(getUserClass(), id);

        generateNewPassword(userAccount);
        UserContext.message(Message.info(NLS.get("UserAccountConroller.passwordGenerated")));

        accounts(ctx);
    }

    /**
     * Generates a new password for the given account and send a mail to the user.
     *
     * @param ctx the current request
     * @param id  the account for which a password is to be created
     */
    @Routed("/user-account/:1/generate-and-send-password")
    @LoginRequired
    @Permission(PERMISSION_MANAGE_USER_ACCOUNTS)
    public void generateAndSendPassword(final WebContext ctx, String id) {
        U userAccount = findForTenant(getUserClass(), id);

        generateNewPassword(userAccount);

        if (userAccount.getUserAccountData().canSendGeneratedPassword()) {
            UserContext.message(Message.info(NLS.fmtr("UserAccountConroller.passwordGeneratedAndSent")
                                                .set(PARAM_EMAIL, userAccount.getUserAccountData().getEmail())
                                                .format()));
            UserContext userContext = UserContext.get();
            userContext.runAs(userContext.getUserManager().findUserByUserId(userAccount.getUniqueName()), () -> {
                Context context = Context.create();

                context.set(PARAM_PASSWORD, userAccount.getUserAccountData().getLogin().getGeneratedPassword())
                       .set(PARAM_NAME, userAccount.getUserAccountData().getPerson().getAddressableName())
                       .set(PARAM_USERNAME, userAccount.getUserAccountData().getLogin().getUsername())
                       .set(PARAM_URL, getBaseUrl())
                       .set(PARAM_ROOT, wondergemRoot);

                mails.createEmail()
                     .to(userAccount.getUserAccountData().getEmail(),
                         userAccount.getUserAccountData().getPerson().toString())
                     .subject(NLS.get("mail-password.subject"))
                     .textTemplate("mail/useraccount/password.pasta", context)
                     .htmlTemplate("mail/useraccount/password.html.pasta", context)
                     .send();
            });
        } else {
            UserContext.message(Message.info(NLS.get("UserAccountConroller.passwordGenerated")));
        }

        accounts(ctx);
    }

    private void generateNewPassword(U userAccount) {
        assertNotNew(userAccount);

        if (!userAccount.getUserAccountData().isPasswordGenerationPossible()) {
            throw Exceptions.createHandled()
                            .withNLSKey("UserAccountConroller.cannotGeneratePasswordForOwnUser")
                            .handle();
        }

        userAccount.getUserAccountData().getLogin().forceGenerationOfPassword();
        userAccount.getMapper().update(userAccount);

        auditLog.neutral("AuditLog.passwordGenerated")
                .causedByCurrentUser()
                .forUser(userAccount.getUniqueName(), userAccount.getUserAccountData().getLogin().getUsername())
                .forTenant(String.valueOf(userAccount.getTenant().getId()),
                           userAccount.getTenant().getValue().getTenantData().getName())
                .log();
    }

    /**
     * Provides a JSON API which re-sends the password to the account with the given email address.
     *
     * @param ctx the current request
     * @param out the JSON response being generated
     */
    @Routed(value = "/forgotPassword", jsonCall = true)
    public void forgotPassword(final WebContext ctx, JSONStructuredOutput out) {
        List<U> accounts = findUserAccountsWithEmail(ctx.get(PARAM_EMAIL).asString().toLowerCase());
        if (accounts.isEmpty()) {
            throw Exceptions.createHandled().withNLSKey("UserAccountController.noUserFoundForEmail").handle();
        }

        if (accounts.size() > 1) {
            throw Exceptions.createHandled().withNLSKey("UserAccountController.tooManyUsersFoundForEmail").handle();
        }

        U account = accounts.get(0);
        if (account.getUserAccountData().getLogin().isAccountLocked()) {
            auditLog.negative("AuditLog.resetPasswordRejected")
                    .causedByUser(account.getUniqueName(), account.getUserAccountData().getLogin().getUsername())
                    .forUser(account.getUniqueName(), account.getUserAccountData().getLogin().getUsername())
                    .forTenant(account.getTenant().getValue().getIdAsString(),
                               account.getTenant().getValue().getTenantData().getName())
                    .log();
            throw Exceptions.createHandled().withNLSKey("LoginData.accountIsLocked").handle();
        }
        account.getUserAccountData().getLogin().forceGenerationOfPassword();

        UserContext userContext = UserContext.get();
        userContext.runAs(userContext.getUserManager().findUserByUserId(account.getUniqueName()),
                          () -> account.getMapper().update(account));

        auditLog.neutral("AuditLog.resetPassword")
                .causedByUser(account.getUniqueName(), account.getUserAccountData().getLogin().getUsername())
                .forUser(account.getUniqueName(), account.getUserAccountData().getLogin().getUsername())
                .forTenant(account.getTenant().getValue().getIdAsString(),
                           account.getTenant().getValue().getTenantData().getName())
                .log();

        if (Strings.isFilled(account.getUserAccountData().getEmail())) {
            userContext.runAs(userContext.getUserManager().findUserByUserId(account.getUniqueName()), () -> {
                Context context = Context.create()
                                         .set(PARAM_REASON,
                                              NLS.fmtr("UserAccountController.forgotPassword.reason")
                                                 .set("ip", ctx.getRemoteIP().toString())
                                                 .format())
                                         .set(PARAM_PASSWORD,
                                              account.getUserAccountData().getLogin().getGeneratedPassword())
                                         .set(PARAM_NAME, account.getUserAccountData().getPerson().getAddressableName())
                                         .set(PARAM_USERNAME, account.getUserAccountData().getLogin().getUsername())
                                         .set(PARAM_URL, getBaseUrl())
                                         .set(PARAM_ROOT, wondergemRoot);
                mails.createEmail()
                     .to(account.getUserAccountData().getEmail(), account.getUserAccountData().getPerson().toString())
                     .subject(NLS.get("mail-password.subject"))
                     .textTemplate("mail/useraccount/password.pasta", context)
                     .htmlTemplate("mail/useraccount/password.html.pasta", context)
                     .send();
            });
        }
    }

    @SuppressWarnings({"unchecked", "RedundantCast"})
    @Explain("The redundant cast is required as otherwise the Java compiler gets confused.")
    protected List<U> findUserAccountsWithEmail(String email) {
        return (List<U>) (Object) mixing.getDescriptor(getUserClass())
                                        .getMapper()
                                        .select(getUserClass())
                                        .eq(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.EMAIL), email)
                                        .limit(2)
                                        .queryList();
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
        Optional<U> account = tryFindForTenant(getUserClass(), id);
        account.ifPresent(u -> {
            if (Objects.equals(getUser().getUserObject(UserAccount.class), u)) {
                throw Exceptions.createHandled().withNLSKey("UserAccountController.cannotDeleteSelf").handle();
            }
        });

        deleteEntity(ctx, account);
        accounts(ctx);
    }

    /**
     * Executes a logout for the current scope.
     *
     * @param ctx the current request
     */
    @Routed("/logout")
    public void logout(WebContext ctx) {
        UserContext.get().getUserManager().logout(ctx);
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
            BasePageHelper<U, ?, ?, ?> ph = getSelectableUsersAsPage();
            ph.withContext(ctx);

            ph.asPage().getItems().forEach(userAccount -> {
                result.accept(new AutocompleteHelper.Completion(userAccount.getUniqueName(),
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
        BasePageHelper<U, ?, ?, ?> ph = getSelectableUsersAsPage();
        ph.withContext(ctx);

        ctx.respondWith()
           .template("templates/biz/tenants/select-user-account.html.pasta", ph.asPage(), isCurrentlySpying(ctx));
    }

    private boolean isCurrentlySpying(WebContext ctx) {
        return ctx.getSessionValue(UserContext.getCurrentScope().getScopeId() + TenantUserManager.SPY_ID_SUFFIX)
                  .isFilled();
    }

    /**
     * Constructs a page helper for the selectable user accounts.
     *
     * @return the list of selectable user accounts wrapped as page helper
     */
    protected abstract BasePageHelper<U, ?, ?, ?> getSelectableUsersAsPage();

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
            auditLog.neutral("AuditLog.switchedToMainUser").causedByCurrentUser().forCurrentUser().log();

            ctx.setSessionValue(UserContext.getCurrentScope().getScopeId() + TenantUserManager.SPY_ID_SUFFIX, null);
            ctx.respondWith().redirectTemporarily("/user-accounts/select");
            return;
        }

        assertPermission(TenantUserManager.PERMISSION_SELECT_USER_ACCOUNT);

        U user = mixing.getDescriptor(getUserClass()).getMapper().find(getUserClass(), id).orElse(null);
        if (user == null) {
            UserContext.get().addMessage(Message.error(NLS.get("UserAccountController.cannotBecomeUser")));
            selectUserAccounts(ctx);
            return;
        }

        if (!UserContext.getCurrentUser().hasPermission(TenantUserManager.PERMISSION_SYSTEM_TENANT)) {
            assertTenant(user);
        }

        auditLog.neutral("AuditLog.selectedUser")
                .causedByCurrentUser()
                .forUser(user.getUniqueName(), user.getUserAccountData().getLogin().getUsername())
                .forTenant(String.valueOf(user.getTenant().getId()),
                           user.getTenant().getValue().getTenantData().getName())
                .log();

        ctx.setSessionValue(UserContext.getCurrentScope().getScopeId() + TenantUserManager.SPY_ID_SUFFIX,
                            user.getUniqueName());
        ctx.respondWith().redirectTemporarily(ctx.get("goto").asString(wondergemRoot));
    }
}
