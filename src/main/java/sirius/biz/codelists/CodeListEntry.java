/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jdbc.codelists;

import sirius.biz.jdbc.model.BizEntity;
import sirius.db.jdbc.SQLEntityRef;
import sirius.db.mixing.Mapping;
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
@Framework(CodeLists.FRAMEWORK_CODE_LISTS)
public class CodeListEntry extends BizEntity {

    /**
     * References the code list this entrd belongs to.
     */
    public static final Mapping CODE_LIST = Mapping.named("codeList");
    private final SQLEntityRef<CodeList> codeList = SQLEntityRef.on(CodeList.class, SQLEntityRef.OnDelete.CASCADE);

    /**
     * Contains the code of the entry.
     */
    public static final Mapping CODE = Mapping.named("code");
    @Trim
    @Length(50)
    @Unique(within = "codeList")
    private String code;

    /**
     * Contains the priority of the entry used for sorting
     */
    public static final Mapping PRIORITY = Mapping.named("priority");
    private int priority = Priorized.DEFAULT_PRIORITY;

    /**
     * Contains the value associated with the code of this entry.
     */
    public static final Mapping VALUE = Mapping.named("value");
    @Trim
    @Length(512)
    @NullAllowed
    private String value;

    /**
     * Contains the additional value associated with the code of this entry.
     */
    public static final Mapping ADDITIONAL_VALUE = Mapping.named("additionalValue");
    @Length(512)
    @NullAllowed
    private String additionalValue;

    /**
     * Contains a description of the value or the entry.
     */
    public static final Mapping DESCRIPTION = Mapping.named("description");
    @Length(1024)
    @NullAllowed
    private String description;

    public SQLEntityRef<CodeList> getCodeList() {
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
