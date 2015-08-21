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
import sirius.kernel.Sirius;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.mixing.Column;
import sirius.mixing.annotations.BeforeSave;
import sirius.mixing.annotations.Length;
import sirius.mixing.annotations.Trim;
import sirius.web.mails.Mails;

/**
 * Created by aha on 07.05.15.
 */
public class UserAccount extends TenantAware {

    @Trim
    @Length(length = 150)
    private String email;
    public static final Column EMAIL = Column.named("email");

    private final PersonData person = new PersonData();
    public static final Column PERSON = Column.named("person");

    private final LoginData login = new LoginData();
    public static final Column LOGIN = Column.named("login");

    private final PermissionData permissions = new PermissionData();
    public static final Column PERMISSIONS = Column.named("permissions");

    @Part
    private static Mails ms;

    @BeforeSave
    public void verifyData() {
        if (Strings.isFilled(email) && !ms.isValidMailAddress(email.trim(), null)) {
            throw Exceptions.createHandled().withNLSKey("Model.invalidEmail").set("value", email).handle();
        }
    }

    public int getMinPasswordLength() {
        return Sirius.getConfig().getInt("security.passwordMinLength");
    }

    public int getSanePasswordLength() {
        return Sirius.getConfig().getInt("security.passwordSaneLength");
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
}
