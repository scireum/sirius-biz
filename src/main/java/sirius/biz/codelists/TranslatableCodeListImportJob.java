/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.biz.importer.format.ImportDictionary;
import sirius.biz.jobs.batch.file.EntityImportJob;
import sirius.biz.jobs.batch.file.ImportMode;
import sirius.biz.jobs.params.BooleanParameter;
import sirius.biz.jobs.params.CodeListParameter;
import sirius.biz.jobs.params.EnumParameter;
import sirius.biz.jobs.params.LanguageParameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.storage.layer3.FileParameter;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;

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
public class TranslatableCodeListImportJob<E extends BaseEntity<?> & CodeListEntry<?, ?, ?>>
        extends EntityImportJob<E> {
    @Part
    private static CodeLists<?, ?, ?> codeLists;

    private final CodeList codeList;
    private final CodeListParameter codeListParameter;
    private final LanguageParameter languageParameter;
    private final Optional<String> language;

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
    public TranslatableCodeListImportJob(FileParameter fileParameter,
                                         BooleanParameter ignoreEmptyParameter,
                                         EnumParameter<ImportMode> importModeParameter,
                                         CodeListParameter codeListParameter,
                                         LanguageParameter languageParameter,
                                         Class<E> type,
                                         ImportDictionary dictionary,
                                         ProcessContext process,
                                         String factoryName) {
        super(fileParameter, ignoreEmptyParameter, importModeParameter, type, dictionary, process, factoryName);
        this.codeList = process.require(codeListParameter);
        this.codeListParameter = codeListParameter;
        this.languageParameter = languageParameter;
        this.language = process.getParameter(languageParameter);
    }

    @Override
    protected E findAndLoad(Context data) {
        data.set(CodeListEntry.CODE_LIST.getName(), process.require(codeListParameter));
        return super.findAndLoad(data);
    }

    /**
     * A new {@link CodeListEntry} only has to be created, if it is not yet present in the selected {@link CodeList}.
     * <p>
     * If a language is selected for the import, updates {@link sirius.biz.translations.Translation Translations}
     * for the {@link CodeListEntry}'s description.
     *
     * @param entity  the entity to persist
     * @param context the row represented as context
     */
    @Override
    protected void createOrUpdate(E entity, Context context) {
        if (!codeLists.getEntry(codeList.getCodeListData().getCode(), entity.getCodeListEntryData().getCode())
                      .isPresent()) {
            super.createOrUpdate(entity, context);
        }
        if (language.isPresent()) {
            // get language code for selected language (e.g. "de" for "Deutsch (de)")
            Optional<Tuple<String, String>> langCode = languageParameter.getValues()
                                                                        .stream()
                                                                        .filter(tuple -> tuple.getSecond()
                                                                                              .equals(language.get()))
                                                                        .findFirst();

            // update the translation text for the selected language
            langCode.ifPresent(tuple -> {
                codeLists.getEntry(codeList.getCodeListData().getCode(), entity.getCodeListEntryData().getCode())
                         .ifPresent(cle -> {
                             cle.getTranslations()
                                .updateText(CodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.DESCRIPTION),
                                            tuple.getFirst(),
                                            entity.getCodeListEntryData().getDescription());
                         });
            });
        }
    }
}
