/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.biz.model.BizEntity;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Priorized;
import sirius.mixing.Column;
import sirius.mixing.EntityRef;
import sirius.mixing.annotations.Length;
import sirius.mixing.annotations.NullAllowed;
import sirius.mixing.annotations.Trim;
import sirius.mixing.annotations.Unique;

/**
 * Created by aha on 11.05.15.
 */
@Framework("code-lists")
public class CodeListEntry extends BizEntity {

    private final EntityRef<CodeList> codeList = EntityRef.on(CodeList.class, EntityRef.OnDelete.CASCADE);
    public static final Column CODE_LIST = Column.named("codeList");

    @Trim
    @Length(length = 50)
    @Unique(within = "codeList")
    private String code;
    public static final Column CODE = Column.named("code");

    private int priority = Priorized.DEFAULT_PRIORITY;
    public static final Column PRIORITY = Column.named("priority");

    @Trim
    @Length(length = 512)
    @NullAllowed
    private String value;
    public static final Column VALUE = Column.named("value");

    @Length(length = 512)
    @NullAllowed
    private String additionalValue;
    public static final Column ADDITIONAL_VALUE = Column.named("additionalValue");

    @Length(length = 1024)
    @NullAllowed
    private String description;
    public static final Column DESCRIPTION = Column.named("description");

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
