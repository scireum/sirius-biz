/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.biz.importer.AutoImport;
import sirius.biz.mongo.PrefixSearchContent;
import sirius.biz.protocol.TraceData;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.AfterDelete;
import sirius.db.mixing.annotations.AfterSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.annotations.Trim;
import sirius.db.mixing.annotations.Unique;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Priorized;

/**
 * Represents an entry in a {@link CodeListData code list}.
 * <p>
 * This is the database representation of the data supplied by {@link CodeLists}.
 */
public class CodeListEntryData extends Composite {

    /**
     * Contains tracing data which records which user created and last edited the entity
     */
    public static final Mapping TRACE = Mapping.named("trace");
    private final TraceData trace = new TraceData();

    /**
     * Contains the code of the entry.
     */
    public static final Mapping CODE = Mapping.named("code");
    @Trim
    @Length(50)
    @Unique(within = "codeList")
    @AutoImport
    @PrefixSearchContent
    private String code;

    /**
     * Contains the priority of the entry used for sorting
     */
    public static final Mapping PRIORITY = Mapping.named("priority");
    @AutoImport
    private int priority = Priorized.DEFAULT_PRIORITY;

    /**
     * Contains the value associated with the code of this entry.
     */
    public static final Mapping VALUE = Mapping.named("value");
    @Trim
    @Length(512)
    @NullAllowed
    @AutoImport
    @PrefixSearchContent
    private String value;

    /**
     * Contains the additional value associated with the code of this entry.
     */
    public static final Mapping ADDITIONAL_VALUE = Mapping.named("additionalValue");
    @Length(512)
    @NullAllowed
    @AutoImport
    @PrefixSearchContent
    private String additionalValue;

    /**
     * Contains a description of the value or the entry.
     */
    public static final Mapping DESCRIPTION = Mapping.named("description");
    @Length(1024)
    @NullAllowed
    @AutoImport
    @PrefixSearchContent
    private String description;

    @Part
    private static CodeLists<?, ?, ?, ?> codeLists;

    @Transient
    private BaseEntity<?> codeListEntry;

    /**
     * Creates a new instance referenced by the given entity.
     *
     * @param codeListEntry the entity to which this entry belongs
     */
    public CodeListEntryData(BaseEntity<?> codeListEntry) {
        this.codeListEntry = codeListEntry;
    }

    @AfterSave
    @AfterDelete
    protected void flushCache() {
        if (!codeListEntry.isNew()) {
            codeLists.clearCache();
        }
    }

    /**
     * Returns the value of the entry which is translated via
     * {@link Value#translate()}.
     *
     * @return the translated value
     */
    public String getTranslatedValue() {
        return Value.of(value).translate().getString();
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

    public TraceData getTrace() {
        return trace;
    }
}
