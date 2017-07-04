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
import sirius.biz.protocol.JournalData;
import sirius.biz.protocol.Journaled;
import sirius.biz.statistics.Statistics;
import sirius.biz.web.Autoloaded;
import sirius.db.mixing.Column;
import sirius.db.mixing.annotations.BeforeDelete;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Index;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.Trim;
import sirius.db.mixing.annotations.Versioned;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.mails.Mails;

import java.util.Optional;

/**
 * Represents a user account which can log into the system.
 * <p>
 * Serveral users are grouped together by their company, which is referred to as {@link Tenant}.
 */
@Framework("tenants")
@Versioned
@Index(name = "index_username", columns = "login_username", unique = true)
public class UserAccount extends TenantAware implements Journaled {

    /**
     * Contains the email address of the user.
     */
    public static final Column EMAIL = Column.named("email");
    @Trim
    @Autoloaded
    @Length(150)
    private String email;

    /**
     * Contains the personal information of the user.
     */
    public static final Column PERSON = Column.named("person");
    private final PersonData person = new PersonData();

    /**
     * Contains the login data used to authenticate the user.
     */
    public static final Column LOGIN = Column.named("login");
    private final LoginData login = new LoginData();

    /**
     * Contains the permissions granted to the user and the custom configuration.
     */
    public static final Column PERMISSIONS = Column.named("permissions");
    private final PermissionData permissions = new PermissionData(this);

    /**
     * Used to record changes on fields of the user.
     */
    public static final Column JOURNAL = Column.named("journal");
    private final JournalData journal = new JournalData(this);

    @Part
    private static Mails ms;

    @Part
    private static Statistics statistics;

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

        assertUnique(LOGIN.inner(LoginData.USERNAME), getLogin().getUsername());
    }

    @BeforeSave
    protected void onModify() {
        TenantUserManager.flushCacheForUserAccount(this);
    }

    @BeforeDelete
    protected void onDelete() {
        statistics.deleteStatistic(getUniqueName());
        TenantUserManager.flushCacheForUserAccount(this);
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
        if (Tenant.class == adapterType) {
            return Optional.ofNullable((A) getTenant().getValue());
        }
        return super.tryAs(adapterType);
    }

    @Override
    public boolean is(Class<?> type) {
        // The TenantUserManager redirects all calls for "Transformable" from "UserInfo" to its manager object.
        // As the Tenant is requested often, we provide a shortcut for "user.as(UserAccount.class).getTenant().getValue()"
        // in the form of "user.as(Tenant.class)"
        if (Tenant.class == type) {
            return true;
        }

        return super.is(type);
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

    @Override
    public JournalData getJournal() {
        return journal;
    }

    @Override
    public String toString() {
        if (Strings.isFilled(getPerson().getFirstname()) || Strings.isFilled(getPerson().getLastname())) {
            return getPerson().toString();
        }
        if (Strings.isFilled(getLogin().getUsername())) {
            return getLogin().getUsername();
        }

        return NLS.get("Model.userAccount");
    }
}
