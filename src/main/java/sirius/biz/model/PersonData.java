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
import sirius.db.mixing.Column;
import sirius.db.mixing.Composite;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Trim;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.Formatter;

/**
 * Provides personal information which can be embedded into other entities or mixins.
 */
public class PersonData extends Composite {

    /**
     * Contains a title.
     */
    public static final Column TITLE = Column.named("title");
    @Length(length = 50)
    @Trim
    @Autoloaded
    @NullAllowed
    private String title;

    /**
     * Contains a salutation.
     * <p>
     * It is expected to be one of the codes in the code list "salutations".
     */
    public static final Column SALUTATION = Column.named("salutation");
    @Length(length = 20)
    @Autoloaded
    @NullAllowed
    @Trim
    private String salutation;

    /**
     * Contains the first name of the person.
     */
    public static final Column FIRSTNAME = Column.named("firstname");
    @Length(length = 150)
    @Trim
    @Autoloaded
    @NullAllowed
    private String firstname;

    /**
     * Contains the last name of the person.
     */
    public static final Column LASTNAME = Column.named("lastname");
    @Length(length = 150)
    @Trim
    @Autoloaded
    @NullAllowed
    private String lastname;

    @Part
    private static CodeLists codeLists;

    /**
     * Generates a string which is used to address the person.
     * <p>
     * An example would be <tt>Mr. Prof. Skip</tt>
     *
     * @return a short string (salutation, title and last name) used to address the person
     */
    public String getAddressableName() {
        return Formatter.create("[${salutation} ][${title} ]${lastname}")
                        .set("salutation", getTranslatedSalutation())
                        .set("title", title)
                        .set("lastname", lastname)
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

    /**
     * Returns the value (translated name) of the saluation.
     *
     * @return the value for <tt>salutation</tt> from the <tt>salutations</tt> code list
     */
    public String getTranslatedSalutation() {
        return codeLists.getValue("salutations", salutation);
    }

    /**
     * Returns the title.
     *
     * @return the title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the title.
     *
     * @param title the title to set
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the salutation.
     *
     * @return the salutation
     */
    public String getSalutation() {
        return salutation;
    }

    /**
     * Sets the salutation.
     *
     * @param salutation the salutation to set
     */
    public void setSalutation(String salutation) {
        this.salutation = salutation;
    }

    /**
     * Returns the firstname.
     *
     * @return the firstname
     */
    public String getFirstname() {
        return firstname;
    }

    /**
     * Sets the firstname.
     *
     * @param firstname the firstname to set
     */
    public void setFirstname(String firstname) {
        this.firstname = firstname;
    }

    /**
     * Returns the lastname.
     *
     * @return the lastname to set
     */
    public String getLastname() {
        return lastname;
    }

    /**
     * Sets the lastname.
     *
     * @param lastname the lastname to set
     */
    public void setLastname(String lastname) {
        this.lastname = lastname;
    }
}
