/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.biz.codelists.CodeLists;
import sirius.biz.web.Autoloaded;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.Formatter;
import sirius.mixing.Column;
import sirius.mixing.Composite;
import sirius.mixing.annotations.Length;
import sirius.mixing.annotations.NullAllowed;
import sirius.mixing.annotations.Trim;

/**
 * Created by aha on 07.05.15.
 */
public class PersonData extends Composite {

    @Length(length = 50)
    @Trim
    @Autoloaded
    @NullAllowed
    private String title;
    public static final Column TITLE = Column.named("title");

    @Length(length = 20)
    @Autoloaded
    @NullAllowed
    private String salutation;
    public static final Column SALUTATION = Column.named("salutation");

    @Length(length = 150)
    @Trim
    @Autoloaded
    @NullAllowed
    private String firstname;
    public static final Column FIRSTNAME = Column.named("firstname");

    @Length(length = 150)
    @Trim
    @Autoloaded
    @NullAllowed
    private String lastname;
    public static final Column LASTNAME = Column.named("lastname");

    @Part
    private static CodeLists codeLists;

    public String getAddressableName() {
        return Formatter.create("[${salutation} ][${title} ]${lastname}")
                        .set("salutation", getTranslatedSalutation())
                        .set("title", title)
                        .set("lastname", lastname)
                        .smartFormat();
    }

    @Override
    public String toString() {
        return Formatter.create("[${salutation} ][${title} ][${firstname} ]${lastname}")
                        .set("salutation", getTranslatedSalutation())
                        .set("title", title)
                        .set("firstname", firstname)
                        .set("lastname", lastname)
                        .smartFormat();
    }

    public String getTranslatedSalutation() {
        return codeLists.getValue("salutations", salutation);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSalutation() {
        return salutation;
    }

    public void setSalutation(String salutation) {
        this.salutation = salutation;
    }

    public String getFirstname() {
        return firstname;
    }

    public void setFirstname(String firstname) {
        this.firstname = firstname;
    }

    public String getLastname() {
        return lastname;
    }

    public void setLastname(String lastname) {
        this.lastname = lastname;
    }
}
