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
import sirius.biz.translations.MultiLanguageString;
import sirius.biz.web.Autoloaded;
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
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.nls.NLS;
import sirius.pasta.noodle.sandbox.NoodleSandbox;

import javax.annotation.Nullable;

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
    @Autoloaded
    @PrefixSearchContent
    private String code;

    /**
     * Contains the priority of the entry used for sorting
     */
    public static final Mapping PRIORITY = Mapping.named("priority");
    @AutoImport
    @Autoloaded
    private int priority = Priorized.DEFAULT_PRIORITY;

    /**
     * Contains the value associated with the code of this entry.
     */
    public static final Mapping VALUE = Mapping.named("value");
    @Trim
    @NullAllowed
    @AutoImport
    @Autoloaded
    @PrefixSearchContent
    private final MultiLanguageString value = new MultiLanguageString().withFallback().withConditionName("code-lists");

    /**
     * Contains the additional value associated with the code of this entry.
     */
    public static final Mapping ADDITIONAL_VALUE = Mapping.named("additionalValue");
    @NullAllowed
    @AutoImport
    @Autoloaded
    @PrefixSearchContent
    private final MultiLanguageString additionalValue =
            new MultiLanguageString().withFallback().withConditionName("code-lists");

    /**
     * Contains a description of the value or the entry.
     */
    public static final Mapping DESCRIPTION = Mapping.named("description");
    @Length(1024)
    @NullAllowed
    @AutoImport
    @Autoloaded
    @PrefixSearchContent
    private String description;

    @Part
    @Nullable
    private static CodeLists<?, ?, ?> codeLists;

    @Transient
    private final BaseEntity<?> codeListEntry;

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
        codeLists.clearCache();
    }

    /**
     * Fetches the translation of the value for the given language.
     *
     * @param language the language to translate to
     * @return the value for the given language or the fallback value
     */
    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public String getTranslatedValue(String language) {
        return value.getText(language).map(NLS::smartGet).orElse(null);
    }

    /**
     * Fetches the translation of the additional value for the given language.
     *
     * @param language the language to translate to
     * @return the additional value for the given language or the fallback value
     */
    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public String getTranslatedAdditionalValue(String language) {
        return additionalValue.getText(language).map(NLS::smartGet).orElse(null);
    }

    @Override
    public String toString() {
        if (Strings.isEmpty(code)) {
            return NLS.get("CodeListEntry.new");
        }

        return code;
    }

    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public MultiLanguageString getValue() {
        return value;
    }

    public MultiLanguageString getAdditionalValue() {
        return additionalValue;
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
