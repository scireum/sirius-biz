/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.biz.codelists.CodeLists;
import sirius.biz.importer.AutoImport;
import sirius.biz.web.Autoloaded;
import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Trim;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.Formatter;

import java.util.Objects;

/**
 * Provides personal information which can be embedded into other entities or mixins.
 */
@SuppressWarnings("squid:S1192")
@Explain("Constants are semantically different")
public class PersonData extends Composite {

    /**
     * Contains a title.
     */
    public static final Mapping TITLE = Mapping.named("title");
    @Length(50)
    @Trim
    @Autoloaded
    @NullAllowed
    @AutoImport
    private String title;

    /**
     * Contains a salutation.
     * <p>
     * It is expected to be one of the codes in the code list "salutations".
     */
    public static final Mapping SALUTATION = Mapping.named("salutation");
    @Length(20)
    @Autoloaded
    @NullAllowed
    @Trim
    @AutoImport
    private String salutation;

    /**
     * Contains the first name of the person.
     */
    public static final Mapping FIRSTNAME = Mapping.named("firstname");
    @Length(150)
    @Trim
    @Autoloaded
    @NullAllowed
    @AutoImport
    private String firstname;

    /**
     * Contains the last name of the person.
     */
    public static final Mapping LASTNAME = Mapping.named("lastname");
    @Length(150)
    @Trim
    @Autoloaded
    @NullAllowed
    @AutoImport
    private String lastname;

    @Part
    private static CodeLists<?, ?, ?> codeLists;

    /**
     * Generates a string which is used to address the person.
     * <p>
     * An example would be <tt>Mr. Prof. Skip</tt>
     *
     * @return a short string (salutation, title and last name) used to address the person
     */
    public String getAddressableName() {
        if (Strings.isEmpty(lastname)) {
            return "";
        }
        return Formatter.create("[${salutation} ][${title} ]${lastname}")
                        .set("salutation", getTranslatedSalutation())
                        .set("title", title)
                        .set("lastname", lastname)
                        .smartFormat();
    }

    /**
     * Generates the name without salutation and title, e.g. Firstname Lastname.
     *
     * @return the first and lastname as string, or empty if the lastname is not filled.
     */
    public String getShortName() {
        if (Strings.isEmpty(lastname)) {
            return "";
        }
        return Formatter.create("[${firstname} ]${lastname}")
                        .set("firstname", getFirstname())
                        .set("lastname", getLastname())
                        .smartFormat();
    }

    /**
     * Generates a string representation of the full name.
     *
     * @return the full name (if filled)
     */
    @Override
    public String toString() {
        return Formatter.create("[${salutation} ][${title} ][${firstname} ]${lastname}")
                        .set("salutation", getTranslatedSalutation())
                        .set("title", title)
                        .set("firstname", firstname)
                        .set("lastname", lastname)
                        .smartFormat();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        if (!(obj instanceof PersonData)) {
            return false;
        }

        return Strings.areEqual(title, ((PersonData) obj).title)
               && Strings.areEqual(salutation,
                                   ((PersonData) obj).salutation)
               && Strings.areEqual(firstname, ((PersonData) obj).firstname)
               && Strings.areEqual(lastname, ((PersonData) obj).lastname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, salutation, firstname, lastname);
    }

    @BeforeSave
    protected void checkSalutation() {
        if (Strings.isFilled(salutation)) {
            // a HandledException will be thrown by if the value is not contained in the codelist
            codeLists.getRequiredValue("salutations", salutation);
        }
    }

    /**
     * Returns the value (translated name) of the saluation.
     *
     * @return the value for <tt>salutation</tt> from the <tt>salutations</tt> code list
     */
    public String getTranslatedSalutation() {
        return codeLists.getTranslatedValue("salutations", salutation);
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
