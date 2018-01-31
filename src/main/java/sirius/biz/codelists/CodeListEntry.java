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
@Framework("biz.code-lists")
public class CodeListEntry extends BizEntity {

    /**
     * References the code list this entrd belongs to.
     */
    public static final Column CODE_LIST = Column.named("codeList");
    private final EntityRef<CodeList> codeList = EntityRef.on(CodeList.class, EntityRef.OnDelete.CASCADE);

    /**
     * Contains the code of the entry.
     */
    public static final Column CODE = Column.named("code");
    @Trim
    @Length(50)
    @Unique(within = "codeList")
    private String code;

    /**
     * Contains the priority of the entry used for sorting
     */
    public static final Column PRIORITY = Column.named("priority");
    private int priority = Priorized.DEFAULT_PRIORITY;

    /**
     * Contains the value associated with the code of this entry.
     */
    public static final Column VALUE = Column.named("value");
    @Trim
    @Length(512)
    @NullAllowed
    private String value;

    /**
     * Contains the additional value associated with the code of this entry.
     */
    public static final Column ADDITIONAL_VALUE = Column.named("additionalValue");
    @Length(512)
    @NullAllowed
    private String additionalValue;

    /**
     * Contains a description of the value or the entry.
     */
    public static final Column DESCRIPTION = Column.named("description");
    @Length(1024)
    @NullAllowed
    private String description;

    public EntityRef<CodeList> getCodeList() {
        return codeList;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getAdditionalValue() {
        return additionalValue;
    }

    public void setAdditionalValue(String additionalValue) {
        this.additionalValue = additionalValue;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }
}
