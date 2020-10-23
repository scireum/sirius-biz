/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.biz.importer.format.FieldDefinition;
import sirius.biz.importer.format.ImportDictionary;
import sirius.biz.jobs.batch.file.EntityImportJob;
import sirius.biz.jobs.batch.file.ImportMode;
import sirius.biz.jobs.params.BooleanParameter;
import sirius.biz.jobs.params.CodeListParameter;
import sirius.biz.jobs.params.EnumParameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.ProcessLink;
import sirius.biz.storage.layer3.FileParameter;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;

import java.util.Optional;

/**
 * Provides a job for importing {@link CodeList CodeLists} in a selected language.
 * <p>
 * The first run of this import job (and any following run without selecting a language) fills the
 * {@link CodeListEntry CodeListEntries} like a regular {@link CodeList} import.
 * <p>
 * If a language is provided, this job creates {@link sirius.biz.translations.Translation translations} accordingly.
 *
 * @param <E> the effective entity type used to represent code list entries
 */
public class CodeListImportJob<E extends BaseEntity<?> & CodeListEntry<?, ?, ?>> extends EntityImportJob<E> {
    @Part
    private static CodeLists<?, ?, ?> codeLists;

    protected static final String CLE_VALUE =
            CodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.VALUE).getName();
    protected static final String CLE_ADDITIONAL_VALUE =
            CodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.ADDITIONAL_VALUE).getName();
    protected static final String CLE_DESCRIPTION =
            CodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.DESCRIPTION).getName();

    private String languagePrefix;

    private final CodeList codeList;
    private final Optional<String> languageParameter;

    /**
     * Creates a new job for the given factory, name and process.
     *
     * @param fileParameter        the parameter which is used to derive the import file from
     * @param ignoreEmptyParameter the parameter which is used to determine if empty values should be ignored
     * @param importModeParameter  the parameter which is used to determine the {@link ImportMode} to use
     * @param codeListParameter    the parameter which specifies the target CodeList
     * @param languageParameter    the parameter which specifies the language for which the CodeList should be imported
     * @param type                 the type of entities being imported
     * @param dictionary           the import dictionary to use
     * @param process              the process context itself
     * @param factoryName          the name of the factory which created this job
     */
    @SuppressWarnings("squid:S00107")
    @Explain("We rather have 9 parameters here and keep the logic properly encapsulated")
    public CodeListImportJob(FileParameter fileParameter,
                             BooleanParameter ignoreEmptyParameter,
                             EnumParameter<ImportMode> importModeParameter,
                             CodeListParameter codeListParameter,
                             Optional<String> languageParameter,
                             Class<E> type,
                             ImportDictionary dictionary,
                             ProcessContext process,
                             String factoryName) {
        super(fileParameter, ignoreEmptyParameter, importModeParameter, type, dictionary, process, factoryName);
        this.codeList = process.require(codeListParameter);
        this.languageParameter = languageParameter;
        languageParameter.ifPresent(lang -> {
            this.languagePrefix = lang + "_";
            this.dictionary.addField(FieldDefinition.stringField(languagePrefix + CLE_VALUE)
                                                    .withLabel(languagePrefix.toUpperCase() + NLS.get(
                                                            "CodeListEntryData.value",
                                                            lang))
                                                    .addAlias(languagePrefix + CLE_VALUE));
            this.dictionary.addField(FieldDefinition.stringField(languagePrefix + CLE_ADDITIONAL_VALUE)
                                                    .withLabel(languagePrefix.toUpperCase() + NLS.get(
                                                            "CodeListEntryData.additionalValue",
                                                            lang))
                                                    .addAlias(languagePrefix + CLE_ADDITIONAL_VALUE));
            this.dictionary.addField(FieldDefinition.stringField(languagePrefix + CLE_DESCRIPTION)
                                                    .withLabel(languagePrefix.toUpperCase() + NLS.get(
                                                            "Model.description",
                                                            lang))
                                                    .addAlias(languagePrefix + CLE_DESCRIPTION));
        });
    }

    @Override
    protected E fillAndVerify(E entity, Context context) {
        languageParameter.ifPresent(lang -> {
            if (context.keySet().stream().noneMatch(key -> key.startsWith(languagePrefix))) {
                throw Exceptions.createHandled().withNLSKey("Translations.importLanguageNotFound").handle();
            }
        });
        return super.fillAndVerify(entity, context);
    }

    @Override
    protected E findAndLoad(Context data) {
        data.set(CodeListEntry.CODE_LIST.getName(), codeList);
        return super.findAndLoad(data);
    }

    @Override
    public void execute() throws Exception {
        process.addLink(new ProcessLink().withLabel("$CodeList").withUri("/code-list/" + codeList.getIdAsString()));
        process.updateTitle(Strings.apply("%s - %s", process.getTitle(), codeList.getCodeListData().getName()));
        super.execute();
    }

    /**
     * Updates {@link sirius.biz.translations.Translation Translations} for the {@link CodeListEntry}, if a language is
     * selected for the import.
     *
     * @param entity  the entity to persist
     * @param context the row represented as context
     */
    @Override
    protected void createOrUpdate(E entity, Context context) {
        super.createOrUpdate(entity, context);

        // update the translation texts for the selected language
        languageParameter.ifPresent(lang -> {
            codeLists.getEntry(codeList.getCodeListData().getCode(), entity.getCodeListEntryData().getCode())
                     .ifPresent(cle -> cle.getTranslations()
                                          .updateText(CodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.VALUE),
                                                      lang,
                                                      context.getValue(languagePrefix + CLE_VALUE).asString()));
            codeLists.getEntry(codeList.getCodeListData().getCode(), entity.getCodeListEntryData().getCode())
                     .ifPresent(cle -> cle.getTranslations()
                                          .updateText(CodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.ADDITIONAL_VALUE),
                                                      lang,
                                                      context.getValue(languagePrefix + CLE_ADDITIONAL_VALUE)
                                                             .asString()));
            codeLists.getEntry(codeList.getCodeListData().getCode(), entity.getCodeListEntryData().getCode())
                     .ifPresent(cle -> cle.getTranslations()
                                          .updateText(CodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.DESCRIPTION),
                                                      lang,
                                                      context.getValue(languagePrefix + CLE_DESCRIPTION).asString()));
        });
    }
}
