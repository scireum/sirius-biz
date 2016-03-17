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
import sirius.biz.web.Autoloaded;
import sirius.kernel.commons.Strings;
import sirius.mixing.Column;
import sirius.mixing.Composite;
import sirius.mixing.annotations.BeforeSave;
import sirius.mixing.annotations.Length;
import sirius.mixing.annotations.NullAllowed;
import sirius.mixing.annotations.Transient;
import sirius.mixing.annotations.Trim;
import sirius.mixing.annotations.Unique;

import java.time.LocalDateTime;

/**
 * Created by aha on 07.05.15.
 */
public class LoginData extends Composite {

    @Trim
    @Autoloaded
    @Unique
    @Length(length = 150)
    private String username;
    public static final Column USERNAME = Column.named("username");

    @Trim
    @Length(length = 50)
    @NullAllowed
    private String passwordHash;
    public static final Column PASSWORD_HASH = Column.named("passwordHash");

    @Trim
    @Length(length = 50)
    @NullAllowed
    private String ucasePasswordHash;
    public static final Column UCASE_PASSWORD_HASH = Column.named("ucasePasswordHash");

    @Trim
    @Length(length = 50)
    private String salt;
    public static final Column SALT = Column.named("salt");

    @Trim
    @Length(length = 50)
    @NullAllowed
    private String generatedPassword;
    public static final Column GENERATED_PASSWORD = Column.named("generatedPassword");

    @Trim
    @Length(length = 50)
    @NullAllowed
    private String apiToken;
    public static final Column API_TOKEN = Column.named("apiToken");

    private int numberOfLogins;
    public static final Column NUMBER_OF_LOGINS = Column.named("numberOfLogins");

    @NullAllowed
    private LocalDateTime lastLogin;
    public static final Column LAST_LOGIN = Column.named("lastLogin");

    @Autoloaded
    private boolean accountLocked;
    public static final Column ACCOUNT_LOCKED = Column.named("accountLocked");

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

    public static String hashPassword(String salt, String password) {
        String hashInput = salt != null ? salt + password : password;
        return BaseEncoding.base64().encode(Hashing.md5().hashString(hashInput, Charsets.UTF_8).asBytes());
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
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

    public boolean isAccountLocked() {
        return accountLocked;
    }

    public void setAccountLocked(boolean accountLocked) {
        this.accountLocked = accountLocked;
    }

    public String getCleartextPassword() {
        return cleartextPassword;
    }

    public void setCleartextPassword(String cleartextPassword) {
        this.cleartextPassword = cleartextPassword;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUcasePasswordHash() {
        return ucasePasswordHash;
    }

    public void setUcasePasswordHash(String ucasePasswordHash) {
        this.ucasePasswordHash = ucasePasswordHash;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }
}
