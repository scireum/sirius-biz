/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists.mongo;

import sirius.biz.codelists.CodeList;
import sirius.biz.codelists.CodeListController;
import sirius.biz.codelists.CodeListEntry;
import sirius.biz.codelists.CodeListEntryData;
import sirius.biz.codelists.CodeLists;
import sirius.biz.importer.format.ImportDictionary;
import sirius.biz.jobs.batch.file.EntityImportJob;
import sirius.biz.jobs.batch.file.EntityImportJobFactory;
import sirius.biz.jobs.params.CodeListParameter;
import sirius.biz.jobs.params.LanguageParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.storage.layer3.FileParameter;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Context;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.http.QueryString;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Provides an import job for {@link MongoCodeList code lists} stored in MongoDB.
 */
@Register(framework = MongoCodeLists.FRAMEWORK_CODE_LISTS_MONGO)
@Permission(CodeListController.PERMISSION_MANAGE_CODELISTS)
public class MongoCodeListImportJobFactory extends EntityImportJobFactory {
    @Part
    private static CodeLists<?, ?, ?, ?> codeLists;

    /**
     * Contains the mongo code list to import the code list entries into.
     */
    private static final CodeListParameter CODE_LIST_PARAMETER =
            new CodeListParameter("codeList", "$CodeList").markRequired();
    private static final LanguageParameter LANGUAGE_PARAMETER =
            new LanguageParameter(LanguageParameter.PARAMETER_NAME, "$LocaleData.lang");

    @Override
    protected EntityImportJob<MongoCodeListEntry> createJob(ProcessContext process) {
        return new CodeListEntryTranslationImportJob<>(fileParameter,
                                                       getDictionary(),
                                                       MongoCodeListEntry.class,
                                                       process,
                                                       CODE_LIST_PARAMETER,
                                                       LANGUAGE_PARAMETER,
                                                       this.getClass().getName());
    }

    @Nonnull
    @Override
    public String getName() {
        return "import-mongo-code-list-entries";
    }

    @Override
    protected Class<? extends BaseEntity<?>> getImportType() {
        return MongoCodeListEntry.class;
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        parameterCollector.accept(CODE_LIST_PARAMETER);
        parameterCollector.accept(LANGUAGE_PARAMETER);
        super.collectParameters(parameterCollector);
    }

    @Override
    protected boolean hasPresetFor(QueryString queryString, Object targetObject) {
        return targetObject instanceof MongoCodeList;
    }

    @Override
    protected void computePresetFor(QueryString queryString, Object targetObject, Map<String, Object> preset) {
        preset.put(CODE_LIST_PARAMETER.getName(), ((MongoCodeList) targetObject).getCodeListData().getCode());
        queryString.get(LANGUAGE_PARAMETER.getName())
                   .ifFilled(value -> preset.put(LANGUAGE_PARAMETER.getName(), value));
    }

    private class CodeListEntryTranslationImportJob<E extends BaseEntity<?> & CodeListEntry<?, ?, ?>>
            extends EntityImportJob<E> {

        private CodeList codeList;
        private Optional<String> language;

        private CodeListEntryTranslationImportJob(FileParameter fileParameter,
                                                  ImportDictionary dictionary,
                                                  Class<E> type,
                                                  ProcessContext process,
                                                  CodeListParameter codeListParameter,
                                                  LanguageParameter languageParameter,
                                                  String factoryName) {
            super(fileParameter, ignoreEmptyParameter, importModeParameter, type, dictionary, process, factoryName);
            this.codeList = process.require(codeListParameter);
            this.language = process.getParameter(LANGUAGE_PARAMETER);
        }

        @Override
        protected E findAndLoad(Context data) {
            data.set(CodeListEntry.CODE_LIST.getName(), process.require(CODE_LIST_PARAMETER));
            return super.findAndLoad(data);
        }

        /**
         * Checks, if the code of the given CodeListEntry is already in its parent CodeList.
         * If yes, adds a new translation for the description of the existing CodeListEntry instead of creating a new entry.
         * Continues with normal import execution otherwise.
         *
         * @param entity
         * @return
         */
        @Override
        protected boolean shouldSkip(E entity) {
            boolean shouldSkip = super.shouldSkip(entity);

            if (language.isPresent()) {
                if (codeLists.getEntry(codeList.getCodeListData().getCode(), entity.getCodeListEntryData().getCode())
                             .isPresent()) {
                    // strip language code from text provided in LanguageParameter (e.g. "deu" from "Deutsch (deu)")
                    String lang = language.get().split("\\(|\\)")[1];
                    codeLists.getEntry(codeList.getCodeListData().getCode(), entity.getCodeListEntryData().getCode())
                             .ifPresent(cle -> {
                                 cle.getTranslations()
                                    .updateText(CodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.DESCRIPTION),
                                                lang,
                                                entity.getCodeListEntryData().getDescription());
                             });
                    shouldSkip = true;
                }
            }

            return shouldSkip;
        }
    }
}
