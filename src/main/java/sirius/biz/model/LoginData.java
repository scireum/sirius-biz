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
import sirius.db.mixing.Column;
import sirius.db.mixing.Composite;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.annotations.Trim;
import sirius.db.mixing.annotations.Unique;
import sirius.kernel.commons.Strings;

import java.time.LocalDateTime;

/**
 * Stores a username and encrypted password along with some trace data to support logins which can be embedded into
 * other entities or mixins.
 * <p>
 * An example of an actual user is {@link sirius.biz.tenants.UserAccount}.
 */
public class LoginData extends Composite {

    /**
     * Contains the username used to identify the account.
     */
    public static final Column USERNAME = Column.named("username");
    @Trim
    @Autoloaded
    @Unique
    @Length(length = 150)
    private String username;

    /**
     * Contains the hashed value of the password to verify the password at login
     */
    public static final Column PASSWORD_HASH = Column.named("passwordHash");
    @Trim
    @Length(length = 50)
    @NullAllowed
    private String passwordHash;

    /**
     * Contains the hash value of the password in all upper case. This is used to support
     * case insensitive password. This is kind of a crazy idea, but some systems need this
     * functionality for legacy reasons.
     */
    public static final Column UCASE_PASSWORD_HASH = Column.named("ucasePasswordHash");
    @Trim
    @Length(length = 50)
    @NullAllowed
    private String ucasePasswordHash;

    /**
     * Contains a random salt which is prepended to the password before hashing to block
     * rainbow tables and the like.
     */
    public static final Column SALT = Column.named("salt");
    @Trim
    @Length(length = 50)
    private String salt;

    /**
     * Contains the generated password as cleartext (so it can be reported to the user). Once the user
     * changes the password, this field will be set to <tt>null</tt>.
     */
    public static final Column GENERATED_PASSWORD = Column.named("generatedPassword");
    @Trim
    @Length(length = 50)
    @NullAllowed
    private String generatedPassword;

    /**
     * Provides an API TOKEN which is crypthgraphically created and can be used as password for technical integrations
     */
    public static final Column API_TOKEN = Column.named("apiToken");
    @Trim
    @Length(length = 50)
    @NullAllowed
    private String apiToken;

    /**
     * Records the number of logins.
     */
    public static final Column NUMBER_OF_LOGINS = Column.named("numberOfLogins");
    @NoJournal
    private int numberOfLogins;

    /**
     * Records the timestamp of the last login.
     */
    public static final Column LAST_LOGIN = Column.named("lastLogin");
    @NoJournal
    @NullAllowed
    private LocalDateTime lastLogin;

    /**
     * Contains a flag which checks if the user is permitted to login.
     */
    public static final Column ACCOUNT_LOCKED = Column.named("accountLocked");
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
     * Returns the password hash.
     *
     * @return the password hash
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Returns the password hash of the upper cased password.
     *
     * @return the password hash of the upper cased password
     */
    public String getUcasePasswordHash() {
        return ucasePasswordHash;
    }

    /**
     * Returns the salt which is used to compute the password hash.
     *
     * @return the salt used for hashing
     */
    public String getSalt() {
        return salt;
    }

    /**
     * Returns the generated password.
     *
     * @return the generated password
     */
    public String getGeneratedPassword() {
        return generatedPassword;
    }

    /**
     * Sets the generated password.
     * <p>
     * Note that a password is auto-generated if no password is present.
     *
     * @param generatedPassword the generated password
     */
    public void setGeneratedPassword(String generatedPassword) {
        this.generatedPassword = generatedPassword;
    }

    /**
     * Returns the number of successful login attempts.
     *
     * @return the number of logins of the associated user
     */
    public int getNumberOfLogins() {
        return numberOfLogins;
    }

    /**
     * Sets the number of logins.
     *
     * @param numberOfLogins the number of logins for this user
     */
    public void setNumberOfLogins(int numberOfLogins) {
        this.numberOfLogins = numberOfLogins;
    }

    /**
     * Returns the timestamp of the last login.
     *
     * @return the last login
     */
    public LocalDateTime getLastLogin() {
        return lastLogin;
    }

    /**
     * Sets the timestamp of the last login.
     *
     * @param lastLogin the last login of the user
     */
    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }

    /**
     * Determines if the account is locked.
     *
     * @return <tt>true</tt> if the user is not allowed to login anymore, <tt>false</tt> otherwise
     */
    public boolean isAccountLocked() {
        return accountLocked;
    }

    /**
     * Locks or unlocks the account.
     *
     * @param accountLocked <tt>true</tt> if the associated user must not login anymore, <tt>false</tt> otherwise
     */
    public void setAccountLocked(boolean accountLocked) {
        this.accountLocked = accountLocked;
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
     */
    public void setCleartextPassword(String cleartextPassword) {
        this.cleartextPassword = cleartextPassword;
    }

    /**
     * Returns the username used at login and to identify the user.
     *
     * @return the username of the associated account
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username used at login and to identify the user.
     *
     * @param username the username of the associated account
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Returns the API token.
     *
     * @return the current API token of the associated account
     */
    public String getApiToken() {
        return apiToken;
    }

    /**
     * Sets the API token.
     *
     * @param apiToken the API token of the associated account
     */
    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }
}
