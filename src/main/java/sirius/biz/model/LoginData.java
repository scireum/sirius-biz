/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import sirius.biz.protocol.NoJournal;
import sirius.biz.web.Autoloaded;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.Composite;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.annotations.Trim;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.health.Exceptions;
import sirius.web.security.UserContext;

import java.time.LocalDateTime;

/**
 * Stores a username and encrypted password along with some trace data to support logins which can be embedded into
 * other entities or mixins.
 * <p>
 * Note that no uniqueness constraint is placed on the username as the context of unqiueness has to be decided by the
 * outside class.
 * <p>
 * An example of an actual user is {@link sirius.biz.tenants.UserAccount}.
 */
public class LoginData extends Composite {

    /**
     * Contains the username used to identify the account.
     */
    public static final Mapping USERNAME = Mapping.named("username");
    @Trim
    @Autoloaded
    @Length(150)
    @NullAllowed
    private String username;

    /**
     * Contains the hashed value of the password to verify the password at login
     */
    public static final Mapping PASSWORD_HASH = Mapping.named("passwordHash");
    @Trim
    @Length(50)
    @NullAllowed
    private String passwordHash;

    /**
     * Contains the hash value of the password in all upper case. This is used to support
     * case insensitive password. This is kind of a crazy idea, but some systems need this
     * functionality for legacy reasons.
     */
    public static final Mapping UCASE_PASSWORD_HASH = Mapping.named("ucasePasswordHash");
    @Trim
    @Length(50)
    @NullAllowed
    private String ucasePasswordHash;

    /**
     * Contains a random salt which is prepended to the password before hashing to block
     * rainbow tables and the like.
     */
    public static final Mapping SALT = Mapping.named("salt");
    @Trim
    @Length(50)
    private String salt;

    /**
     * Contains the generated password as cleartext (so it can be reported to the user). Once the user
     * changes the password, this field will be set to <tt>null</tt>.
     */
    public static final Mapping GENERATED_PASSWORD = Mapping.named("generatedPassword");
    @Trim
    @Length(50)
    @NullAllowed
    private String generatedPassword;

    /**
     * Provides an API TOKEN which is crypthgraphically created and can be used as password for technical integrations
     */
    public static final Mapping API_TOKEN = Mapping.named("apiToken");
    @Trim
    @Length(50)
    @NullAllowed
    private String apiToken;

    /**
     * Records the number of logins.
     */
    public static final Mapping NUMBER_OF_LOGINS = Mapping.named("numberOfLogins");
    @NoJournal
    private int numberOfLogins;

    /**
     * Records the timestamp of the last login.
     */
    public static final Mapping LAST_LOGIN = Mapping.named("lastLogin");
    @NoJournal
    @NullAllowed
    private LocalDateTime lastLogin;

    /**
     * Records the timestamp of the last login via an external system.
     * <p>
     * When using external identity poviders, like SAML, we want to keep track when the last login via this happened
     * as we probably want to enforce regular validations (logins).
     */
    public static final Mapping LAST_EXTERNAL_LOGIN = Mapping.named("lastExternalLogin");
    @NoJournal
    @NullAllowed
    private LocalDateTime lastExternalLogin;

    /**
     * Contains a flag which checks if the user is permitted to login.
     */
    public static final Mapping ACCOUNT_LOCKED = Mapping.named("accountLocked");
    @Autoloaded
    private boolean accountLocked;

    @Transient
    private String cleartextPassword;

    @BeforeSave
    protected void autofill() {
        if (Strings.isFilled(cleartextPassword)) {
            cleartextPassword = cleartextPassword.trim();
            if (Strings.isFilled(cleartextPassword)) {
                this.salt = Strings.generateCode(20);
                this.passwordHash = hashPassword(salt, cleartextPassword);
                this.ucasePasswordHash = hashPassword(salt, cleartextPassword.toUpperCase());
                this.generatedPassword = null;
            }
        }
        if (Strings.isEmpty(passwordHash) && Strings.isEmpty(generatedPassword)) {
            this.generatedPassword = Strings.generatePassword();
        }
        if (Strings.isFilled(generatedPassword)) {
            this.salt = Strings.generateCode(20);
            this.passwordHash = hashPassword(salt, generatedPassword);
            this.ucasePasswordHash = hashPassword(salt, generatedPassword.toUpperCase());
        }
        if (Strings.isEmpty(apiToken)) {
            this.apiToken = Strings.generateCode(32);
        }
    }

    /**
     * Verifys the given password if it meets the length requirement and is equal to its confirmation.
     *
     * @param password          the password to check for
     * @param confirmation      the confirmation password to check for
     * @param minPasswordLength the minimum password length
     * @throws sirius.kernel.health.HandledException if password is too short or if passwords do mismatch
     */
    public void verifyPassword(String password, String confirmation, int minPasswordLength) {
        if (Strings.isEmpty(password) || password.length() < minPasswordLength) {
            UserContext.setFieldError("password", null);
            throw Exceptions.createHandled()
                            .withNLSKey("Model.password.minLengthError")
                            .set("minChars", minPasswordLength)
                            .handle();
        }

        if (!Strings.areEqual(password, confirmation)) {
            UserContext.setFieldError("confirmation", null);
            throw Exceptions.createHandled().withNLSKey("Model.password.confirmationMismatch").handle();
        }
    }

    /**
     * Computes a password hash for a given salt and password.
     *
     * @param salt     the salt to use
     * @param password the password to hash
     * @return a cryptographic one way hash based on the salt and password
     */
    public static String hashPassword(String salt, String password) {
        String hashInput = salt != null ? salt + password : password;
        return BaseEncoding.base64().encode(Hashing.md5().hashString(hashInput, Charsets.UTF_8).asBytes());
    }

    /**
     * Checks if the given password is correct.
     *
     * @param password    the password to validate
     * @param defaultSalt the salt used as a fallback
     * @return <tt>true</tt> if the password is valid, <tt>false</tt> otherwise
     */
    public boolean checkPassword(String password, String defaultSalt) {
        String givenPasswordHash = LoginData.hashPassword(Value.of(salt).asString(defaultSalt), password);

        return givenPasswordHash.equals(passwordHash);
    }

    /**
     * Returns the currently set password in cleartext.
     * <p>
     * Note that this value is transient and therefore not saved to the database.
     *
     * @return the currently applied password in cleatext
     */
    public String getCleartextPassword() {
        return cleartextPassword;
    }

    /**
     * Sets the password in cleartext.
     * <p>
     * Note that this value is transient and therefore not saved to the database. This field only exists for
     * convenience, as it can be filled and the associated entity can be save. All hashes will be updated accordingly.
     *
     * @param cleartextPassword the password as clear text
     */
    public void setCleartextPassword(String cleartextPassword) {
        this.cleartextPassword = cleartextPassword;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getUcasePasswordHash() {
        return ucasePasswordHash;
    }

    public String getSalt() {
        return salt;
    }

    public String getGeneratedPassword() {
        return generatedPassword;
    }

    public void setGeneratedPassword(String generatedPassword) {
        this.generatedPassword = generatedPassword;
    }

    public int getNumberOfLogins() {
        return numberOfLogins;
    }

    public void setNumberOfLogins(int numberOfLogins) {
        this.numberOfLogins = numberOfLogins;
    }

    public LocalDateTime getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }

    public LocalDateTime getLastExternalLogin() {
        return lastExternalLogin;
    }

    public void setLastExternalLogin(LocalDateTime lastExternalLogin) {
        this.lastExternalLogin = lastExternalLogin;
    }

    public boolean isAccountLocked() {
        return accountLocked;
    }

    public void setAccountLocked(boolean accountLocked) {
        this.accountLocked = accountLocked;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }
}
