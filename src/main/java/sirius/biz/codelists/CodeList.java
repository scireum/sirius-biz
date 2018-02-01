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
@Framework("biz.code-lists")
public class CodeList extends BizEntity {

    /**
     * Contains the unique code or short name which identifies the code list.
     */
    public static final Column CODE = Column.named("code");
    @Trim
    @Autoloaded
    @Length(50)
    @Unique
    private String code;

    /**
     * Contains a descriptive name of the list which is show in the administration GUI.
     */
    public static final Column NAME = Column.named("name");
    @Trim
    @Autoloaded
    @NullAllowed
    @Length(150)
    private String name;

    /**
     * Contains a description of the purpose and use of the code list.
     */
    public static final Column DESCRIPTION = Column.named("description");
    @Trim
    @Autoloaded
    @NullAllowed
    @Length(1024)
    private String description;

    /**
     * Determines if yet unknown entries should be auto created with an identity mapping.
     * <p>
     * Using this approach, the code list will fill itself and a user can provide descriptive texts later.
     */
    public static final Column AUTO_FILL = Column.named("autofill");
    @Autoloaded
    private boolean autofill = true;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isAutofill() {
        return autofill;
    }

    public void setAutofill(boolean autofill) {
        this.autofill = autofill;
    }
}
