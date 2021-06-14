/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.biz.mongo.PrefixSearchContent;
import sirius.biz.protocol.TraceData;
import sirius.biz.web.Autoloaded;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.AfterDelete;
import sirius.db.mixing.annotations.AfterSave;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.annotations.Trim;
import sirius.db.mixing.annotations.Unique;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;

/**
 * Represents all fields which are required to describe a code list.
 */
public class CodeListData extends Composite {

    /**
     * Contains tracing data which records which user created and last edited the entity
     */
    public static final Mapping TRACE = Mapping.named("trace");
    private final TraceData trace = new TraceData();

    /**
     * Contains the unique code or short name which identifies the code list.
     */
    public static final Mapping CODE = Mapping.named("code");
    @Trim
    @Autoloaded
    @Length(50)
    @Unique(within = "tenant")
    @PrefixSearchContent
    private String code;

    /**
     * Contains a descriptive name of the list which is show in the administration GUI.
     */
    public static final Mapping NAME = Mapping.named("name");
    @Trim
    @Autoloaded
    @NullAllowed
    @Length(150)
    @PrefixSearchContent
    private String name;

    /**
     * Contains a description of the purpose and use of the code list.
     */
    public static final Mapping DESCRIPTION = Mapping.named("description");
    @Trim
    @Autoloaded
    @NullAllowed
    @Length(1024)
    @PrefixSearchContent
    private String description;

    /**
     * Determines if yet unknown entries should be auto created with an identity mapping.
     * <p>
     * Using this approach, the code list will fill itself and a user can provide descriptive texts later.
     */
    public static final Mapping AUTO_FILL = Mapping.named("autofill");
    @Autoloaded
    private boolean autofill = true;

    @Part
    @Nullable
    private static CodeLists<?, ?, ?> codeLists;

    @Transient
    private BaseEntity<?> codeList;

    /**
     * Creates a new instance referenced by the given entity.
     *
     * @param codeList the entity to which this list belongs
     */
    public CodeListData(BaseEntity<?> codeList) {
        this.codeList = codeList;
    }

    @BeforeSave
    protected void checkName() {
        if (Strings.isFilled(code) && code.contains("|")) {
            throw Exceptions.createHandled().withNLSKey("CodeList.noPipeAllowed").handle();
        }
    }

    @AfterSave
    @AfterDelete
    protected void flushCache() {
        if (!codeList.isNew()) {
            codeLists.valueCache.clear();
        }
    }

    @Override
    public String toString() {
        if (Strings.isEmpty(name)) {
            return NLS.get("CodeList.new");
        }

        return name;
    }

    public TraceData getTrace() {
        return trace;
    }

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
