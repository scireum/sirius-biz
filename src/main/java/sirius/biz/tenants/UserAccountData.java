/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.codelists.LookupValue;
import sirius.biz.importer.AutoImport;
import sirius.biz.model.LoginData;
import sirius.biz.model.PermissionData;
import sirius.biz.model.PersonData;
import sirius.biz.storage.layer2.BlobHardRef;
import sirius.biz.storage.layer2.URLBuilder;
import sirius.biz.util.Languages;
import sirius.biz.web.Autoloaded;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.AfterDelete;
import sirius.db.mixing.annotations.AfterSave;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.Lob;
import sirius.db.mixing.annotations.LowerCase;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.annotations.Trim;
import sirius.db.mixing.annotations.ValidatedBy;
import sirius.db.mixing.types.StringList;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Json;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Message;
import sirius.web.mails.Mails;
import sirius.web.security.MessageProvider;
import sirius.web.security.UserContext;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Represents a user account which can log into the system.
 * <p>
 * Several users are grouped together by their company, which is referred to as {@link Tenant}.
 */
public class UserAccountData extends Composite implements MessageProvider {

    /**
     * Defines the storage space used by user accounts.
     */
    public static final String STORAGE_SPACE = "user-accounts";

    /**
     * Contains the fallback URI used by {@link #fetchSmallUrl()}, {@link #fetchMediumUrl()}, and {@link #fetchLargeUrl()}.
     */
    public static final String IMAGE_FALLBACK_URI = "/assets/images/user_image_fallback.png";

    /**
     * Contains the name of the variant used to fetch the small image.
     */
    public static final String IMAGE_VARIANT_SMALL = "user-small";

    /**
     * Contains the name of the variant used to fetch the medium image.
     */
    public static final String IMAGE_VARIANT_MEDIUM = "user-medium";

    /**
     * Contains the name of the variant used to fetch the large image.
     */
    public static final String IMAGE_VARIANT_LARGE = "user-large";

    @Transient
    private final BaseEntity<?> userObject;

    /**
     * Contains the email address of the user.
     */
    public static final Mapping EMAIL = Mapping.named("email");
    @Trim
    @LowerCase
    @Autoloaded
    @Length(150)
    @NullAllowed
    @AutoImport
    @ValidatedBy(EmailAddressValidator.class)
    private String email;

    /**
     * Contains the reference to the image file.
     */
    public static final Mapping IMAGE = Mapping.named("image");
    @Autoloaded
    @NullAllowed
    private final BlobHardRef image = new BlobHardRef(STORAGE_SPACE);

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
     * Contains all sub scopes to user is restricted to.
     * <p>
     * If this field is empty, the user has access to <b>ALL</b> sub scopes as this is the common case.
     */
    public static final Mapping SUB_SCOPES = Mapping.named("subScopes");
    @Autoloaded
    @NullAllowed
    @AutoImport
    @Lob
    private final StringList subScopes = new StringList();

    /**
     * Determines if an external login is required from time to time.
     */
    public static final Mapping EXTERNAL_LOGIN_REQUIRED = Mapping.named("externalLoginRequired");
    @Autoloaded
    @AutoImport
    private boolean externalLoginRequired = false;

    /**
     * The language of the {@link UserAccount}.
     */
    public static final Mapping LANGUAGE = Mapping.named("language");
    @Autoloaded
    @NullAllowed
    @Length(2)
    private final LookupValue language = new LookupValue(Languages.LOOKUP_TABLE_ACTIVE_LANGUAGES);

    /**
     * Contains internally stored settings for this user.
     * <p>
     * This might e.g. be display settings for specific views or the like.
     */
    public static final Mapping USER_PREFERENCES = Mapping.named("userPreferences");
    @NullAllowed
    @Lob
    private String userPreferences;

    @Transient
    private Map<String, Object> parsedUserPreferences;

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
        userObject.ifChangedAndFilled(UserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON)
                                                                   .inner(PersonData.SALUTATION),
                                      getPerson()::verifySalutation);

        fillAndVerifyUsername();
    }

    private void fillAndVerifyUsername() {
        transferEmailToLoginIfEmpty();

        // Ensure that the username is filled and unique...
        userObject.verifyIfChangedFailIfEmpty(UserAccount.USER_ACCOUNT_DATA.inner(LOGIN).inner(LoginData.USERNAME),
                                              () -> {
                                                  // Make it lowercase...
                                                  getLogin().setUsername(getLogin().getUsername().toLowerCase());

                                                  // Ensure uniqueness...
                                                  userObject.assertUnique(UserAccount.USER_ACCOUNT_DATA.inner(LOGIN)
                                                                                                       .inner(LoginData.USERNAME),
                                                                          getLogin().getUsername());
                                              });
    }

    /**
     * If this user has no {@link LoginData#USERNAME}, fill it with {@link #EMAIL}.
     */
    public void transferEmailToLoginIfEmpty() {
        // Use email address if no explicit username is present
        if (Strings.isEmpty(getLogin().getUsername())) {
            getLogin().setUsername(getEmail());
        }
    }

    @AfterSave
    protected void onModify() {
        TenantUserManager.flushCacheForUserAccount((UserAccount<?, ?>) userObject);
    }

    @AfterDelete
    protected void onDelete() {
        TenantUserManager.flushCacheForUserAccount((UserAccount<?, ?>) userObject);
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
        return ((UserAccount<?, ?>) userObject).getTenant().fetchValue();
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

    /**
     * Generates a string representation of this user.
     * By default this uses the full Name {@link PersonData#toString()}
     * If {@link #hasName} is false, returns {@link LoginData#getUsername()}.
     * If this is also empty, {@link #email)} is returned.
     * As last option an anonymous identifier is used.
     *
     * @return a string representation of this user
     */
    @Override
    public String toString() {
        if (hasName()) {
            return getPerson().toString();
        }
        if (Strings.isFilled(getLogin().getUsername())) {
            return getLogin().getUsername();
        }
        if (Strings.isFilled(email)) {
            return email;
        }

        return NLS.get("Model.userAccount");
    }

    /**
     * Generates a short name, for this user.
     * By default this is "Firstname Lastname", if the lastname is filled.
     * If {@link #hasName} is false, {@link #toString} is called.
     *
     * @return a short name for this user
     */
    public String getShortName() {
        if (hasName()) {
            return getPerson().getShortName();
        }
        return toString();
    }

    /**
     * Determines if the user has a real name.
     *
     * @return <tt>true</tt> if a real name was provided, <tt>false</tt> otherwise
     */
    public boolean hasName() {
        return Strings.isFilled(getPerson().getLastname());
    }

    /**
     * Generates a string which is used to address the person.
     * Depending on the filled fields this will result in either calling
     * Default {@link PersonData#getAddressableName()} e.g. <tt>Mr. Foo Bar</tt>
     * If neither {@link PersonData#getSalutation()} or {@link PersonData#getTitle()} is set, {@link #toString} is called.
     *
     * @return a short string used to address the person
     */
    public String getAddressableName() {
        if (hasName() && (getPerson().getSalutation().isFilled() || Strings.isFilled(getPerson().getTitle()))) {
            return getPerson().getAddressableName();
        }
        return toString();
    }

    @Override
    public void addMessages(Consumer<Message> messageConsumer) {
        if (Strings.isFilled(getLogin().getGeneratedPassword())) {
            messageConsumer.accept(Message.warn()
                                          .withTextAndLink(NLS.get("UserAccount.warnAboutGeneratedPassword"),
                                                           NLS.get("UserAccount.changePassword"),
                                                           "/profile/password"));
        }

        warnAboutForcedLogout(messageConsumer);
    }

    private void warnAboutForcedLogout(Consumer<Message> messageConsumer) {
        if (isExternalLoginRequired() && isNearInterval(getLogin().getLastExternalLogin(),
                                                        getTenant().getTenantData().getExternalLoginIntervalDays())) {
            messageConsumer.accept(Message.info().withTextMessage(NLS.get("UserAccount.forcedExternalLoginNear")));
            return;
        }

        if (isNearInterval(getLogin().getLastLogin(), getTenant().getTenantData().getLoginIntervalDays())) {
            messageConsumer.accept(Message.info().withTextMessage(NLS.get("UserAccount.forcedLogoutNear")));
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

    /**
     * Returns the parsed user preferences.
     * <p>
     * Use {@link UserAccount#readPreference(String)} to access the preferences.
     *
     * @return the parsed user preferences as map
     */
    public Map<String, Object> fetchUserPreferences() {
        if (parsedUserPreferences == null) {
            if (Strings.isFilled(userPreferences)) {
                parsedUserPreferences = Json.convertToMap(Json.parseObject(userPreferences));
            } else {
                parsedUserPreferences = Collections.emptyMap();
            }
        }

        return Collections.unmodifiableMap(parsedUserPreferences);
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

    public BlobHardRef getImage() {
        return image;
    }

    public boolean isExternalLoginRequired() {
        return externalLoginRequired;
    }

    public void setExternalLoginRequired(boolean externalLoginRequired) {
        this.externalLoginRequired = externalLoginRequired;
    }

    @Deprecated
    public LookupValue getLang() {
        return language;
    }

    public LookupValue getLanguage() {
        return language;
    }

    public StringList getSubScopes() {
        return subScopes;
    }

    /**
     * Builds a URL to the small image.
     *
     * @return a URLBuilder which is used to fetch the small image of this user
     */
    public URLBuilder fetchSmallUrl() {
        return image.url().withFallbackUri(IMAGE_FALLBACK_URI).withVariant(IMAGE_VARIANT_SMALL);
    }

    /**
     * Builds a URL to the medium image.
     *
     * @return a URLBuilder which is used to fetch the medium image of this user
     */
    public URLBuilder fetchMediumUrl() {
        return image.url().withFallbackUri(IMAGE_FALLBACK_URI).withVariant(IMAGE_VARIANT_MEDIUM);
    }

    /**
     * Builds a URL to the large image.
     *
     * @return a URLBuilder which is used to fetch the large image of this user
     */
    public URLBuilder fetchLargeUrl() {
        return image.url().withFallbackUri(IMAGE_FALLBACK_URI).withVariant(IMAGE_VARIANT_LARGE);
    }
}
