/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.biz.model.BizEntity;
import sirius.biz.web.Autoloaded;
import sirius.db.mixing.Column;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Trim;
import sirius.db.mixing.annotations.Unique;
import sirius.kernel.di.std.Framework;

/**
 * Represents a list for name value pairs which can be managed by the user.
 * <p>
 * This is the database representation of the data supplied by {@link CodeLists}.
 */
@Framework("code-lists")
public class CodeList extends BizEntity {

    /**
     * Contains the unique code or short name which identifies the code list.
     */
    public static final Column CODE = Column.named("code");
    @Trim
    @Autoloaded
    @Length(length = 50)
    @Unique
    private String code;

    /**
     * Contains a descriptive name of the list which is show in the administration GUI.
     */
    public static final Column NAME = Column.named("name");
    @Trim
    @Autoloaded
    @NullAllowed
    @Length(length = 150)
    private String name;

    /**
     * Contains a description of the purpose and use of the code list.
     */
    public static final Column DESCRIPTION = Column.named("description");
    @Trim
    @Autoloaded
    @NullAllowed
    @Length(length = 1024)
    private String description;

    /**
     * Determines if yet unknown entries should be auto created with an identity mapping.
     * <p>
     * Using this approach, the code list will fill itself and a user can provide descriptive texts later.
     */
    public static final Column AUTO_FILL = Column.named("autofill");
    @Autoloaded
    private boolean autofill = true;

    /**
     * Returns the code of the code list
     *
     * @return the unqiue code of the list
     */
    public String getCode() {
        return code;
    }

    /**
     * Sets the code of the code list
     *
     * @param code the qunique code of the list
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * Returns the name of the code list
     *
     * @return the name of the list
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the code list
     *
     * @param name the name of the code list
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the description of the code list
     *
     * @return the description of the code list
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of the code list
     *
     * @param description the description of the code list
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Determines if the code list should auto fill itself
     *
     * @return <tt>true</tt> if unknown entries will be auto created, <tt>false</tt> otherwise
     */
    public boolean isAutofill() {
        return autofill;
    }

    /**
     * Sets the auto fill behaviour
     *
     * @param autofill <tt>true</tt> if the code list should create unknown entries, <tt>false</tt> otherwise
     */
    public void setAutofill(boolean autofill) {
        this.autofill = autofill;
    }
}
