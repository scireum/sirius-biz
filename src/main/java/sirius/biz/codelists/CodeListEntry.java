/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.biz.model.BizEntity;
import sirius.db.mixing.Column;
import sirius.db.mixing.EntityRef;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Trim;
import sirius.db.mixing.annotations.Unique;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Priorized;

/**
 * Represents a en entry in a {@link CodeList}.
 * <p>
 * This is the database representation of the data supplied by {@link CodeLists}.
 */
@Framework("code-lists")
public class CodeListEntry extends BizEntity {

    /**
     * References the code list this entrd belongs to.
     */
    private final EntityRef<CodeList> codeList = EntityRef.on(CodeList.class, EntityRef.OnDelete.CASCADE);
    public static final Column CODE_LIST = Column.named("codeList");

    /**
     * Contains the code of the entry.
     */
    @Trim
    @Length(length = 50)
    @Unique(within = "codeList")
    private String code;
    public static final Column CODE = Column.named("code");

    /**
     * Contains the priority of the entry used for sorting
     */
    private int priority = Priorized.DEFAULT_PRIORITY;
    public static final Column PRIORITY = Column.named("priority");

    /**
     * Contains the value associated with the code of this entry.
     */
    @Trim
    @Length(length = 512)
    @NullAllowed
    private String value;
    public static final Column VALUE = Column.named("value");

    /**
     * Contains the additional value associated with the code of this entry.
     */
    @Length(length = 512)
    @NullAllowed
    private String additionalValue;
    public static final Column ADDITIONAL_VALUE = Column.named("additionalValue");

    /**
     * Contains a description of the value or the entry.
     */
    @Length(length = 1024)
    @NullAllowed
    private String description;
    public static final Column DESCRIPTION = Column.named("description");

    /**
     * Returns the reference to the code list to which this entry belongs.
     *
     * @return the code list of this entry
     */
    public EntityRef<CodeList> getCodeList() {
        return codeList;
    }

    /**
     * Returns the value associated with the code of this entry.
     *
     * @return the value of this entry
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the value associated with the code of this entry.
     *
     * @param value the value associated with this entry
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * The code represented by this entry.
     *
     * @return the code of this entry
     */
    public String getCode() {
        return code;
    }

    /**
     * Sets the code of this entry.
     *
     * @param code the code of this entry
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * Returns the additional value associated with the code of this entry.
     *
     * @return the additional value of this entry
     */
    public String getAdditionalValue() {
        return additionalValue;
    }

    /**
     * Sets the additional value associated with the code of this entry.
     *
     * @param additionalValue the additional value associated with this entry
     */
    public void setAdditionalValue(String additionalValue) {
        this.additionalValue = additionalValue;
    }

    /**
     * Returns the description of this entry.
     *
     * @return the description of this entry
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of the entry.
     *
     * @param description the description of the entry
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns the sort priority of this entry.
     *
     * @return the priority of this entry
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Sets the sort priority of this entry.
     *
     * @param priority the priority of this entry
     */
    public void setPriority(int priority) {
        this.priority = priority;
    }
}
