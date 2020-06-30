/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.biz.importer.AutoImport;
import sirius.biz.protocol.NoJournal;
import sirius.biz.web.Autoloaded;
import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.annotations.Trim;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.PriorityParts;
import sirius.kernel.health.Exceptions;
import sirius.web.security.UserContext;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
     * Describes the possible outcome of {@link #verifyPassword(String, String, int)}.
     */
    public enum PasswordVerificationResult {

        /**
         * The given password was valid and is stored as strong cryptographic
         * hash / KDF
         */
        VALID,

        /**
         * The given password was valid but is stored using a potentially weak
         * hash function / KDF and should therefore be re-hashed using {@link #hashPassword(String, String)}
         */
        VALID_NEEDS_RE_HASH,

        /**
         * The given password is invalid.
         */
        INVALID
    }

    /**
     * Contains the username used to identify the account.
     */
    public static final Mapping USERNAME = Mapping.named("username");
    @Trim
    @Autoloaded
    @Length(150)
    @NullAllowed
    @AutoImport
    private String username;

    /**
     * Contains the hashed value of the password to verify the password at login
     */
    public static final Mapping PASSWORD_HASH = Mapping.named("passwordHash");
    @Trim
    @Length(50)
    @NullAllowed
    @NoJournal
    private String passwordHash;

    /**
     * Contains a random salt which is prepended to the password before hashing to block
     * rainbow tables and the like.
     */
    public static final Mapping SALT = Mapping.named("salt");
    @Trim
    @Length(50)
    @NullAllowed
    @NoJournal
    private String salt;

    /**
     * Contains a generated string which is put into each client session of the user.
     * <p>
     * Once the user deciedes to change the password or to log out on all devices,
     * the fingerprint is updated and all sessions containing the old finderprint
     * are considered invalid.
     */
    public static final Mapping FINGERPRINT = Mapping.named("fingerprint");
    @NoJournal
    @Length(50)
    @NullAllowed
    private String fingerprint;

    /**
     * Contains the generated password as cleartext (so it can be reported to the user). Once the user
     * changes the password, this field will be set to <tt>null</tt>.
     */
    public static final Mapping GENERATED_PASSWORD = Mapping.named("generatedPassword");
    @Trim
    @Length(50)
    @NullAllowed
    @AutoImport
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
     * Records the timestamp when the user was last seen.
     * <p>
     * Note that this is only updated once per day.
     */
    public static final Mapping LAST_SEEN = Mapping.named("lastSeen");
    @NoJournal
    @NullAllowed
    private LocalDate lastSeen;

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
     * Records the timestamp of the last password change.
     */
    public static final Mapping LAST_PASSWORD_CHANGE = Mapping.named("lastPasswordChange");
    @NoJournal
    @NullAllowed
    private LocalDateTime lastPasswordChange;

    /**
     * Contains a flag which checks if the user is permitted to login.
     */
    public static final Mapping ACCOUNT_LOCKED = Mapping.named("accountLocked");
    @Autoloaded
    @AutoImport
    private boolean accountLocked;

    /**
     * Contains a cleartext password that has just been created by a user or generated by the system.
     * <p>
     * This only exists in memory and will be hashed into {@link #PASSWORD_HASH} with a {@link #SALT} when this entity is saved.
     */
    @Transient
    private String cleartextPassword;

    /**
     * Skips the creation of {@link #generatedPassword} on save which is normally used to fill in {@link #passwordHash} with a generated {@link #salt}.
     * <p>
     * If set to true, this will allow for users without a valid password login to exist in the database.
     * This is useful for user objects with other means to login, like {@link #API_TOKEN} or custom auth methods.
     */
    @Transient
    private boolean skipPasswordCreation;

    @PriorityParts(PasswordHashFunction.class)
    private static List<PasswordHashFunction> hashFunctions;

    @BeforeSave
    protected void autofill() {
        // If there is no password set at all, generate one...
        if (!skipPasswordCreation && Strings.isEmpty(passwordHash) && Strings.isEmpty(generatedPassword)) {
            this.generatedPassword = Strings.generatePassword();
        }

        // If the generated password has been changed, mark it as the password to be set 
        if (Strings.isEmpty(cleartextPassword)
            && Strings.isFilled(generatedPassword)
            && checkPassword(username, generatedPassword) != PasswordVerificationResult.VALID) {
            this.cleartextPassword = generatedPassword;
        }

        // If a non-empty password is to be set (either given by the user or via a generated password),
        // execute the password change by hashing the password with a salt and updating all internal fields
        if (Strings.isFilled(cleartextPassword)) {
            cleartextPassword = cleartextPassword.trim();
            if (Strings.isFilled(cleartextPassword)) {
                this.salt = Strings.generateCode(20);
                this.passwordHash = hashPassword(salt, cleartextPassword);
                this.fingerprint = null;
                this.lastPasswordChange = LocalDateTime.now();
                if (!Strings.areEqual(cleartextPassword, generatedPassword)) {
                    this.generatedPassword = null;
                }
            }
        }

        if (Strings.isEmpty(apiToken)) {
            this.apiToken = Strings.generateCode(32);
        }
        if (Strings.isEmpty(fingerprint)) {
            this.fingerprint = Strings.generateCode(12);
        }
    }

    /**
     * Clears all internal fields so that a new password will be generated when the underlying entity is saved.
     */
    public void forceGenerationOfPassword() {
        this.generatedPassword = null;
        this.passwordHash = null;
        this.skipPasswordCreation = false;
    }

    /**
     * Resets the fingerprint so that the user will be logged out on all devices as
     * all client sessions become invalid.
     */
    public void resetFingerprint() {
        this.fingerprint = null;
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
    public static String hashPassword(@Nonnull String salt, @Nonnull String password) {
        if (Strings.isEmpty(salt)) {
            throw new IllegalArgumentException("A salt is required when computing a password hash.");
        }
        if (Strings.isEmpty(password)) {
            throw new IllegalArgumentException("Cannot compute a hash for an empty password.");
        }
        for (PasswordHashFunction hashFunction : hashFunctions) {
            if (!hashFunction.isOutdated()) {
                String hash = hashFunction.computeHash(null, salt, password);
                if (Strings.isFilled(hash)) {
                    return hash;
                }
            }
        }

        throw new IllegalStateException("No strong password hash function is available.");
    }

    /**
     * Checks if the given password is correct.
     *
     * @param username the username for which the password was given - some legacy implementations use the username to
     *                 derive the password hash
     * @param password the password to validate
     * @return a tuple where the first parameter determines if the password is valid
     */
    public PasswordVerificationResult checkPassword(String username, String password) {
        if (!Strings.isFilled(passwordHash)) {
            return PasswordVerificationResult.INVALID;
        }
        for (PasswordHashFunction hashFunction : hashFunctions) {
            String givenPasswordHash = hashFunction.computeHash(username, salt, password);
            if (Strings.isFilled(givenPasswordHash) && Strings.areEqual(givenPasswordHash, passwordHash)) {
                return hashFunction.isOutdated() ?
                       PasswordVerificationResult.VALID_NEEDS_RE_HASH :
                       PasswordVerificationResult.VALID;
            }
        }
        return PasswordVerificationResult.INVALID;
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

    /**
     * Determines if the generated password should be displayed.
     *
     * @return <tt>true</tt> if the generated password should be displayed, <tt>false</tt> otherwise
     */
    public boolean isDisplayGeneratedPassword() {
        if (Strings.isEmpty(generatedPassword) || lastPasswordChange == null) {
            return false;
        }

        Duration showGeneratedPasswordFor =
                Duration.ofMillis(Sirius.getSettings().getMilliseconds("security.showGeneratedPasswordFor"));

        if (showGeneratedPasswordFor == null) {
            return false;
        }

        return Duration.between(lastPasswordChange, LocalDateTime.now()).compareTo(showGeneratedPasswordFor) < 0;
    }

    /**
     * Can be used to disable auto password generation on save.
     * <p>
     * This only sets a flag that is not saved to database.
     * If you want to disable it altogether for an entity containing loginData, a good option would be to implement a
     * {@link BeforeSave beforeSaveHandler} with priority < 100 that calls this method.
     *
     * @see #autofill()
     */
    public void skipPasswordCreation() {
        skipPasswordCreation = true;
    }

    @Override
    public String toString() {
        return username;
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

    public String getFingerprint() {
        return fingerprint;
    }

    public String getSalt() {
        return salt;
    }

    public String getPasswordHash() {
        return passwordHash;
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

    public LocalDateTime getLastLogin() {
        return lastLogin;
    }

    public LocalDateTime getLastExternalLogin() {
        return lastExternalLogin;
    }

    public LocalDateTime getLastPasswordChange() {
        return lastPasswordChange;
    }

    public LocalDate getLastSeen() {
        return lastSeen;
    }
}
