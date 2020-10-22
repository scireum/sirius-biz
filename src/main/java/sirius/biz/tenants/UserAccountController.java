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
import sirius.biz.packages.Packages;
import sirius.biz.protocol.AuditLog;
import sirius.biz.web.BasePageHelper;
import sirius.biz.web.BizController;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.info.Product;
import sirius.kernel.nls.NLS;
import sirius.web.controller.AutocompleteHelper;
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Message;
import sirius.web.controller.Page;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.mails.Mails;
import sirius.web.security.LoginRequired;
import sirius.web.security.Permission;
import sirius.web.security.Permissions;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;
import sirius.web.services.JSONStructuredOutput;

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
     * The permission required to add, modify or lock accounts.
     */
    public static final String PERMISSION_MANAGE_USER_ACCOUNTS = "permission-manage-user-accounts";

    /**
     * The permission required to add, modify or lock accounts of the system tenant.
     */
    public static final String PERMISSION_MANAGE_SYSTEM_USERS = "permission-manage-system-users";

    /**
     * The feature required to provide a custom config per user account.
     */
    public static final String FEATURE_USER_ACCOUNT_CONFIG = "feature-user-account-config";

    /**
     * The permission required to delete accounts.
     */
    public static final String PERMISSION_DELETE_USER_ACCOUNTS = "permission-delete-user-accounts";

    private static final String PARAM_PASSWORD = "password";
    private static final String PARAM_NAME = "name";
    private static final String PARAM_USERNAME = "username";
    private static final String PARAM_URL = "url";
    private static final String PARAM_ROOT = "root";
    private static final String PARAM_EMAIL = "email";
    private static final String PARAM_REASON = "reason";
    private static final String LIST_ROUTE = "/user-accounts";

    @Part
    protected Mails mails;

    @ConfigValue("product.wondergemRoot")
    protected String wondergemRoot;

    @ConfigValue("security.roles")
    protected List<String> roles;

    @Part
    protected AuditLog auditLog;

    @Part
    private Packages packages;

    /**
     * We provide a custom field for the tenant helper.
     * <p>
     * In contrast to <{@link #tenants}, this will have the generic arguments applied and is therefore
     * fully aware of the exact classes to use.
     */
    @Part
    private Tenants<I, T, U> matchingTenants;

    /**
     * Shows a list of all available users of the current tenant.
     *
     * @param webContext the current request
     */
    @Routed(LIST_ROUTE)
    @DefaultRoute
    @LoginRequired
    public void accounts(WebContext webContext) {
        assertProperUserManagementPermission();
        Page<U> accounts =
                getUsersAsPage(webContext).addBooleanFacet(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                                                        .inner(LoginData.ACCOUNT_LOCKED)
                                                                                        .toString(),
                                                           NLS.get("LoginData.accountLocked")).asPage();

        webContext.respondWith().template("/templates/biz/tenants/user-accounts.html.pasta", accounts, getUserClass());
    }

    /**
     * Ensures that the current user is permitted to manage the user accounts for the current tenant.
     * <p>
     * This is made public so that other controllers which enhance the user management can re-use the logic.
     */
    public static void assertProperUserManagementPermission() {
        UserInfo currentUser = UserContext.getCurrentUser();
        boolean isCurrentTenantSystemTenant = currentUser.tryAs(Tenant.class)
                                                         .map(tenant -> tenant.hasPermission(Tenant.PERMISSION_SYSTEM_TENANT))
                                                         .orElse(false);
        if (isCurrentTenantSystemTenant) {
            currentUser.assertPermission(PERMISSION_MANAGE_SYSTEM_USERS);
        } else {
            currentUser.assertPermission(PERMISSION_MANAGE_USER_ACCOUNTS);
        }
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
     * @param webContext the current request
     * @return the list of available user accounts wrapped as page helper
     */
    protected abstract BasePageHelper<U, ?, ?, ?> getUsersAsPage(WebContext webContext);

    /**
     * Shows an editor for the given account.
     *
     * @param webContext the current request
     * @param accountId  the {@link UserAccount} to edit
     */
    @Routed("/user-account/:1")
    @LoginRequired
    public void account(WebContext webContext, String accountId) {
        assertProperUserManagementPermission();

        U userAccount = findForTenant(getUserClass(), accountId);

        boolean requestHandled = prepareSave(webContext).withAfterCreateURI("/user-account/${id}")
                                                        .withAfterSaveURI(LIST_ROUTE)
                                                        .withPreSaveHandler(isNew -> {
                                                            if (isUserLockingHimself(userAccount)) {
                                                                throw Exceptions.createHandled()
                                                                                .withNLSKey(
                                                                                        "UserAccountController.cannotLockSelf")
                                                                                .handle();
                                                            }

                                                            List<String> accessiblePermissions = getRoles();
                                                            packages.loadAccessiblePermissions(webContext.getParameters(
                                                                    "roles"),
                                                                                               accessiblePermissions::contains,
                                                                                               userAccount.getUserAccountData()
                                                                                                          .getPermissions()
                                                                                                          .getPermissions()
                                                                                                          .modify());
                                                        })
                                                        .saveEntity(userAccount);

        if (!requestHandled) {
            validate(userAccount);
            webContext.respondWith()
                      .template("/templates/biz/tenants/user-account-details.html.pasta", userAccount, this);
        }
    }

    private boolean isUserLockingHimself(U userAccount) {
        if (!userAccount.isChanged(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
                                                                .inner(LoginData.ACCOUNT_LOCKED))) {
            return false;
        }
        if (!userAccount.getUserAccountData().getLogin().isAccountLocked()) {
            return false;
        }
        return Objects.equals(getUser().getUserObject(UserAccount.class), userAccount);
    }

    /**
     * Returns a list of supported languages and their translated name.
     *
     * @return a list of tuples containing the ISO code and the translated name
     */
    public List<Tuple<String, String>> getAvailableLanguages() {
        return tenants.getTenantUserManager().getAvailableLanguages();
    }

    /**
     * Shows an editor for the custom configuration of the given user.
     *
     * @param webContext the current request
     * @param accountId  the id of the account which config will be edited
     */
    @Routed("/user-account/:1/config")
    @LoginRequired
    @Permission(FEATURE_USER_ACCOUNT_CONFIG)
    public void accountConfig(WebContext webContext, String accountId) {
        assertProperUserManagementPermission();

        U userAccount = findForTenant(getUserClass(), accountId);
        assertNotNew(userAccount);
        webContext.respondWith().template("/templates/biz/tenants/user-account-config.html.pasta", userAccount);
    }

    /**
     * Provides a JSON API to change the settings of an account, including its configuration.
     *
     * @param webContext the current request
     * @param jsonOutput the JSON response being generated
     * @param accountId  the id of the account to update
     */
    @Routed(value = "/user-account/:1/config/update", jsonCall = true)
    @LoginRequired
    @Permission(FEATURE_USER_ACCOUNT_CONFIG)
    public void updateAccountConfig(WebContext webContext, JSONStructuredOutput jsonOutput, String accountId) {
        assertProperUserManagementPermission();

        U userAccount = findForTenant(getUserClass(), accountId);
        assertNotNew(userAccount);

        String configFieldName = UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERMISSIONS)
                                                              .inner(PermissionData.CONFIG_STRING)
                                                              .getName();
        if (webContext.hasParameter(configFieldName)) {
            // Reads configuration manually to prevent altering other fields
            String config = webContext.getParameter(configFieldName);
            userAccount.getUserAccountData().getPermissions().setConfigString(config);
            // parses the config to make sure it is valid
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
        Tenant<?> tenant = tenants.getRequiredTenant();
        return packages.filterAccessiblePermissions(roles, tenant::hasPermission);
    }

    /**
     * Returns the translated name of a role.
     *
     * @param role the role to translate
     * @return a translated name for the given role
     */
    public String getRoleName(String role) {
        return Permissions.getTranslatedPermission(role);
    }

    /**
     * Returns a description of the role.
     *
     * @param role the role to fetch the description for
     * @return the description of the given role
     */
    public String getRoleDescription(String role) {
        return Permissions.getPermissionDescription(role);
    }

    /**
     * Generates a new password for the given account.
     *
     * @param webContext the current request
     * @param accountId  the account for which a password is to be created
     */
    @Routed("/user-account/:1/generate-password")
    @LoginRequired
    public void generatePassword(final WebContext webContext, String accountId) {
        assertProperUserManagementPermission();

        U userAccount = findForTenant(getUserClass(), accountId);

        generateNewPassword(userAccount);
        UserContext.message(Message.info(NLS.get("UserAccountConroller.passwordGenerated")));

        webContext.respondWith().redirectToGet(LIST_ROUTE);
    }

    /**
     * Generates a new password for the given account and send a mail to the user.
     *
     * @param webContext the current request
     * @param accountId  the account for which a password is to be created
     */
    @Routed("/user-account/:1/generate-and-send-password")
    @LoginRequired
    public void generateAndSendPassword(final WebContext webContext, String accountId) {
        assertProperUserManagementPermission();

        U userAccount = findForTenant(getUserClass(), accountId);

        generateNewPassword(userAccount);

        if (userAccount.getUserAccountData().canSendGeneratedPassword()) {
            UserContext.message(Message.info(NLS.fmtr("UserAccountConroller.passwordGeneratedAndSent")
                                                .set(PARAM_EMAIL, userAccount.getUserAccountData().getEmail())
                                                .format()));
            UserContext userContext = UserContext.get();
            userContext.runAs(userContext.getUserManager().findUserByUserId(userAccount.getUniqueName()), () -> {
                Context mailContext = Context.create();

                mailContext.set(PARAM_PASSWORD, userAccount.getUserAccountData().getLogin().getGeneratedPassword())
                           .set(PARAM_NAME, userAccount.getUserAccountData().getAddressableName())
                           .set(PARAM_USERNAME, userAccount.getUserAccountData().getLogin().getUsername())
                           .set(PARAM_URL, getBaseUrl())
                           .set(PARAM_REASON,
                                NLS.fmtr("UserAccountController.generatedPassword.reason")
                                   .set("product", Product.getProduct().getName())
                                   .format())
                           .set(PARAM_ROOT, wondergemRoot);

                mails.createEmail()
                     .to(userAccount.getUserAccountData().getEmail(), userAccount.getUserAccountData().toString())
                     .subject(NLS.fmtr("UserAccountController.generatedPassword.subject")
                                 .set("product", Product.getProduct().getName())
                                 .format())
                     .textTemplate("/mail/useraccount/password.pasta", mailContext)
                     .htmlTemplate("/mail/useraccount/password.html.pasta", mailContext)
                     .send();
            });
        } else {
            UserContext.message(Message.info(NLS.get("UserAccountConroller.passwordGenerated")));
        }

        webContext.respondWith().redirectToGet(LIST_ROUTE);
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
                           matchingTenants.fetchCachedRequiredTenant(userAccount.getTenant()).getTenantData().getName())
                .log();
    }

    /**
     * Provides a JSON API which re-sends the password to the account with the given email address.
     *
     * @param webContext the current request
     * @param jsonOutput the JSON response being generated
     */
    @Routed(value = "/forgotPassword", jsonCall = true)
    public void forgotPassword(final WebContext webContext, JSONStructuredOutput jsonOutput) {
        List<U> accounts = findUserAccountsWithEmail(webContext.get(PARAM_EMAIL).asString().toLowerCase());
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
                    .forTenant(account.getTenant().getIdAsString(),
                               matchingTenants.fetchCachedRequiredTenant(account.getTenant()).getTenantData().getName())
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
                .forTenant(account.getTenant().getIdAsString(),
                           matchingTenants.fetchCachedRequiredTenant(account.getTenant()).getTenantData().getName())
                .log();

        if (Strings.isFilled(account.getUserAccountData().getEmail())) {
            userContext.runAs(userContext.getUserManager().findUserByUserId(account.getUniqueName()), () -> {
                Context context = Context.create()
                                         .set(PARAM_REASON,
                                              NLS.fmtr("UserAccountController.forgotPassword.reason")
                                                 .set("ip", webContext.getRemoteIP().toString())
                                                 .format())
                                         .set(PARAM_PASSWORD,
                                              account.getUserAccountData().getLogin().getGeneratedPassword())
                                         .set(PARAM_NAME, account.getUserAccountData().getAddressableName())
                                         .set(PARAM_USERNAME, account.getUserAccountData().getLogin().getUsername())
                                         .set(PARAM_URL, getBaseUrl())
                                         .set(PARAM_ROOT, wondergemRoot);
                mails.createEmail()
                     .to(account.getUserAccountData().getEmail(), account.getUserAccountData().toString())
                     .subject(NLS.get("UserAccountController.forgotPassword.subject"))
                     .textTemplate("/mail/useraccount/password.pasta", context)
                     .htmlTemplate("/mail/useraccount/password.html.pasta", context)
                     .send();
            });
        }
    }

    @SuppressWarnings("unchecked")
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
     * Locks the given account.
     *
     * @param webContext the current request
     * @param accountId  the account to lock
     */
    @LoginRequired
    @Routed("/user-account/:1/lock")
    public void lockUser(final WebContext webContext, String accountId) {
        assertProperUserManagementPermission();

        Optional<U> account = tryFindForTenant(getUserClass(), accountId);
        account.ifPresent(user -> {
            if (Objects.equals(getUser().getUserObject(UserAccount.class), user)) {
                throw Exceptions.createHandled().withNLSKey("UserAccountController.cannotLockSelf").handle();
            }

            user.getUserAccountData().getLogin().setAccountLocked(true);
            user.getMapper().update(user);
        });

        webContext.respondWith().redirectToGet(LIST_ROUTE);
    }

    /**
     * Unlocks the given account.
     *
     * @param webContext the current request
     * @param accountId  the account to unlock
     */
    @LoginRequired
    @Routed("/user-account/:1/unlock")
    public void unlockUser(final WebContext webContext, String accountId) {
        assertProperUserManagementPermission();

        Optional<U> account = tryFindForTenant(getUserClass(), accountId);
        account.ifPresent(user -> {
            user.getUserAccountData().getLogin().setAccountLocked(false);
            user.getMapper().update(user);
        });

        webContext.respondWith().redirectToGet(LIST_ROUTE);
    }

    /**
     * Deletes the given account.
     *
     * @param webContext the current request
     * @param accountId  the account to delete
     */
    @LoginRequired
    @Routed("/user-account/:1/delete")
    @Permission(PERMISSION_DELETE_USER_ACCOUNTS)
    public void deleteAdmin(final WebContext webContext, String accountId) {
        assertProperUserManagementPermission();

        Optional<U> account = tryFindForTenant(getUserClass(), accountId);
        account.ifPresent(u -> {
            if (Objects.equals(getUser().getUserObject(UserAccount.class), u)) {
                throw Exceptions.createHandled().withNLSKey("UserAccountController.cannotDeleteSelf").handle();
            }
        });

        deleteEntity(webContext, account);
        webContext.respondWith().redirectToGet(LIST_ROUTE);
    }

    /**
     * Executes a logout for the current scope.
     *
     * @param webContext the current request
     */
    @Routed("/logout")
    public void logout(WebContext webContext) {
        UserContext.get().getUserManager().logout(webContext);
        webContext.respondWith().redirectToGet(wondergemRoot);
    }

    /**
     * Autocompletion for UserAccounts.
     * <p>
     * Only accepts UserAccounts which belong to the current Tenant.
     *
     * @param webContext the current request
     */
    @LoginRequired
    @Routed("/user-accounts/autocomplete")
    public void usersAutocomplete(final WebContext webContext) {
        AutocompleteHelper.handle(webContext, (query, result) -> {
            Page<U> accounts = getUsersAsPage(webContext).asPage();
            accounts.getItems().forEach(userAccount -> {
                result.accept(new AutocompleteHelper.Completion(userAccount.getUniqueName(),
                                                                userAccount.toString(),
                                                                userAccount.toString()));
            });
        });
    }

    /**
     * Lists all users which the current user can "become" (switch to).
     *
     * @param webContext the current request
     */
    @Routed("/user-accounts/select")
    @LoginRequired
    @Permission(TenantUserManager.PERMISSION_SELECT_USER_ACCOUNT)
    public void selectUserAccounts(WebContext webContext) {
        Page<U> selectableUsers = getSelectableUsersAsPage().withContext(webContext).asPage();
        fillTenantsFromCache(selectableUsers);

        webContext.respondWith()
                  .template("/templates/biz/tenants/select-user-account.html.pasta",
                            selectableUsers,
                            isCurrentlySpying(webContext));
    }

    private void fillTenantsFromCache(Page<U> selectableUsers) {
        selectableUsers.getItems()
                       .forEach(user -> user.getTenant()
                                            .setValue(matchingTenants.fetchCachedTenant(user.getTenant())
                                                                     .orElse(null)));
    }

    private boolean isCurrentlySpying(WebContext webContext) {
        return webContext.getSessionValue(UserContext.getCurrentScope().getScopeId() + TenantUserManager.SPY_ID_SUFFIX)
                         .isFilled();
    }

    /**
     * Constructs a page helper for the selectable user accounts.
     *
     * @return the list of selectable user accounts wrapped as page helper
     */
    protected abstract BasePageHelper<U, ?, ?, ?> getSelectableUsersAsPage();

    /**
     * Switches from the current user to the given user.
     * <p>
     * The current user can act on the behalf of the given user, he will appear as he is that user,
     * and he will have the roles the given user has.
     * The only permissions kept from the original user may be {@link TenantUserManager#PERMISSION_SYSTEM_TENANT_AFFILIATE},
     * and {@link TenantUserManager#PERMISSION_SELECT_USER_ACCOUNT} (to switch back).
     * Additionatly, the permission {@link TenantUserManager#PERMISSION_SPY_USER} is given, so the system can identify the user switch.
     *
     * @param webContext the current request
     * @param accountId  the id of the user to switch to
     */
    @LoginRequired
    @Routed("/user-accounts/select/:1")
    public void selectUserAccount(final WebContext webContext, String accountId) {
        if ("main".equals(accountId)) {
            String originalUserId = tenants.getTenantUserManager().getOriginalUserId();
            UserAccount<?, ?> account = tenants.getTenantUserManager().fetchAccount(originalUserId);
            auditLog.neutral("AuditLog.switchedToMainUser")
                    .hideFromUser()
                    .causedByUser(account.getUniqueName(), account.getUserAccountData().getLogin().getUsername())
                    .forCurrentUser()
                    .log();

            webContext.setSessionValue(UserContext.getCurrentScope().getScopeId() + TenantUserManager.SPY_ID_SUFFIX,
                                       null);
            webContext.respondWith().redirectTemporarily("/user-accounts/select");
            return;
        }

        assertPermission(TenantUserManager.PERMISSION_SELECT_USER_ACCOUNT);

        U user = mixing.getDescriptor(getUserClass()).getMapper().find(getUserClass(), accountId).orElse(null);
        if (user == null) {
            UserContext.get().addMessage(Message.error(NLS.get("UserAccountController.cannotBecomeUser")));
            selectUserAccounts(webContext);
            return;
        }

        if (!getUser().hasPermission(TenantUserManager.PERMISSION_SYSTEM_TENANT_AFFILIATE)) {
            assertTenant(user);
        }

        auditLog.neutral("AuditLog.selectedUser")
                .hideFromUser()
                .causedByCurrentUser()
                .forUser(user.getUniqueName(), user.getUserAccountData().getLogin().getUsername())
                .forTenant(user.getTenant().getIdAsString(),
                           matchingTenants.fetchCachedRequiredTenant(user.getTenant()).getTenantData().getName())
                .log();

        webContext.setSessionValue(UserContext.getCurrentScope().getScopeId() + TenantUserManager.SPY_ID_SUFFIX,
                                   user.getUniqueName());
        webContext.respondWith().redirectTemporarily(webContext.get("goto").asString(wondergemRoot));
    }
}
