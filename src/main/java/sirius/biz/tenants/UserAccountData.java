/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.importer.AutoImport;
import sirius.biz.model.LoginData;
import sirius.biz.model.PermissionData;
import sirius.biz.model.PersonData;
import sirius.biz.web.Autoloaded;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.AfterDelete;
import sirius.db.mixing.annotations.AfterSave;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.annotations.Trim;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Message;
import sirius.web.mails.Mails;
import sirius.web.security.MessageProvider;
import sirius.web.security.UserContext;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Represents a user account which can log into the system.
 * <p>
 * Serveral users are grouped together by their company, which is referred to as {@link Tenant}.
 */
public class UserAccountData extends Composite implements MessageProvider {

    @Transient
    private final BaseEntity<?> userObject;

    /**
     * Contains the email address of the user.
     */
    public static final Mapping EMAIL = Mapping.named("email");
    @Trim
    @Autoloaded
    @Length(150)
    @NullAllowed
    @AutoImport
    private String email;

    /**
     * Contains the personal information of the user.
     */
    public static final Mapping PERSON = Mapping.named("person");
    private final PersonData person = new PersonData();

    /**
     * Contains the login data used to authenticate the user.
     */
    public static final Mapping LOGIN = Mapping.named("login");
    private final LoginData login = new LoginData();

    /**
     * Contains the permissions granted to the user and the custom configuration.
     */
    public static final Mapping PERMISSIONS = Mapping.named("permissions");
    private final PermissionData permissions;

    /**
     * Determines if an external login is required from time to time.
     */
    public static final Mapping EXTERNAL_LOGIN_REQUIRED = Mapping.named("externalLoginRequired");
    @Autoloaded
    @AutoImport
    private boolean externalLoginRequired = false;

    @Part
    private static Mails ms;

    /**
     * Creates a new instance referenced by the given entity.
     *
     * @param userObject the entity to which this user data belongs
     */
    public UserAccountData(BaseEntity<?> userObject) {
        this.userObject = userObject;
        this.permissions = new PermissionData(userObject);
    }

    @BeforeSave
    protected void verifyData() {
        if (Strings.isFilled(email)) {
            if (ms.isValidMailAddress(email.trim(), null)) {
                email = email.toLowerCase();
            } else {
                throw Exceptions.createHandled().withNLSKey("Model.invalidEmail").set("value", email).handle();
            }
        }

        if (Strings.isEmpty(getLogin().getUsername())) {
            getLogin().setUsername(getEmail());
        }
        if (Strings.isFilled(getLogin().getUsername())) {
            getLogin().setUsername(getLogin().getUsername().toLowerCase());
        } else {
            throw Exceptions.createHandled()
                            .withNLSKey("Property.fieldNotNullable")
                            .set("field", NLS.get("LoginData.username"))
                            .handle();
        }

        userObject.assertUnique(UserAccount.USER_ACCOUNT_DATA.inner(LOGIN).inner(LoginData.USERNAME), getLogin().getUsername());
    }

    @AfterSave
    protected void onModify() {
        TenantUserManager.flushCacheForUserAccount((UserAccount<?,?>)userObject);
    }

    @AfterDelete
    protected void onDelete() {
        TenantUserManager.flushCacheForUserAccount((UserAccount<?,?>)userObject);
    }

    /**
     * Contains the minimal length of a password to be accepted.
     *
     * @return the minimal length of a password to be accepted
     */
    public int getMinPasswordLength() {
        return Sirius.getSettings().getInt("security.passwordMinLength");
    }

    /**
     * Contains the minimal length of a sane password.
     *
     * @return the minimal length for a password to be considered sane / good / not totally unsafe
     */
    public int getSanePasswordLength() {
        return Sirius.getSettings().getInt("security.passwordSaneLength");
    }

    @SuppressWarnings("unchecked")
    @Override
    public <A> Optional<A> tryAs(Class<A> adapterType) {
        // The TenantUserManager redirects all calls for "Transformable" from "UserInfo" to its manager object.
        // As the Tenant is requested often, we provide a shortcut for "user.as(UserAccount.class).getTenant().getValue()"
        // in the form of "user.as(Tenant.class)"
        if (Tenant.class.isAssignableFrom(adapterType)) {
            return Optional.ofNullable((A) getTenant());
        }

        return super.tryAs(adapterType);
    }

    protected Tenant<?> getTenant() {
        return ((UserAccount<?,?>)userObject).getTenant().getValue();
    }

    @Override
    public boolean is(Class<?> type) {
        // The TenantUserManager redirects all calls for "Transformable" from "UserInfo" to its manager object.
        // As the Tenant is requested often, we provide a shortcut for "user.as(UserAccount.class).getTenant().getValue()"
        // in the form of "user.as(Tenant.class)"
        if (Tenant.class.isAssignableFrom(type)) {
            return true;
        }

        return super.is(type);
    }

    @Override
    public String toString() {
        if (hasName()) {
            return getPerson().toString();
        }
        if (Strings.isFilled(getLogin().getUsername())) {
            return getLogin().getUsername();
        }

        return NLS.get("Model.userAccount");
    }

    /**
     * Determines if the user has a real name.
     *
     * @return <tt>true</tt> if a real name was provided, <tt>false</tt> otherwise
     */
    public boolean hasName() {
        return Strings.isFilled(getPerson().getLastname());
    }

    @Override
    public void addMessages(Consumer<Message> messageConsumer) {
        if (Strings.isFilled(getLogin().getGeneratedPassword())) {
            messageConsumer.accept(Message.warn(NLS.get("UserAccount.warnAboutGeneratedPassword"))
                                          .withAction("/profile/password", NLS.get("UserAccount.changePassword")));
        }

        warnAboutForcedLogout(messageConsumer);
    }

    private void warnAboutForcedLogout(Consumer<Message> messageConsumer) {
        if (isExternalLoginRequired()) {
            if (isNearInterval(getLogin().getLastExternalLogin(),
                               getTenant().getTenantData().getExternalLoginIntervalDays())) {
                messageConsumer.accept(Message.info(NLS.get("UserAccount.forcedExternalLoginNear")));
                return;
            }
        }
        if (isNearInterval(getLogin().getLastLogin(), getTenant().getTenantData().getLoginIntervalDays())) {
            messageConsumer.accept(Message.info(NLS.get("UserAccount.forcedLogoutNear")));
        }
    }

    private boolean isNearInterval(LocalDateTime dateTime, Integer requiredInterval) {
        if (requiredInterval == null) {
            return false;
        }

        if (dateTime == null) {
            return true;
        }

        long actualInterval = Duration.between(dateTime, LocalDateTime.now()).toDays();
        return actualInterval >= requiredInterval - 3;
    }

    /**
     * Determines if the current user is able to generate the password for <tt>this</tt> user.
     *
     * @return <tt>true</tt> if the current user can generate a password, <tt>false</tt> otherwise
     */
    public boolean isPasswordGenerationPossible() {
        return !Objects.equals(UserContext.getCurrentUser().as(UserAccount.class), userObject);
    }

    /**
     * Determines if generated passwords can be sent to <tt>this</tt> user.
     *
     * @return <tt>true</tt> if generated passwords can be sent to the user, <tt>false</tt> otherwise
     */
    public boolean canSendGeneratedPassword() {
        return Strings.isFilled(email) && userObject.isUnique(UserAccount.USER_ACCOUNT_DATA.inner(EMAIL), email);
    }

    public PersonData getPerson() {
        return person;
    }

    public LoginData getLogin() {
        return login;
    }

    public PermissionData getPermissions() {
        return permissions;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isExternalLoginRequired() {
        return externalLoginRequired;
    }

    public void setExternalLoginRequired(boolean externalLoginRequired) {
        this.externalLoginRequired = externalLoginRequired;
    }
}
