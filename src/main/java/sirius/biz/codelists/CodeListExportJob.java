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
import sirius.biz.jobs.batch.file.EntityExportJob;
import sirius.biz.jobs.batch.file.ExportFileType;
import sirius.biz.jobs.params.EnumParameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.ProcessLink;
import sirius.biz.storage.layer3.FileOrDirectoryParameter;
import sirius.biz.storage.layer3.FileParameter;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.query.Query;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Strings;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Provides a job for exporting {@link CodeList CodeLists} in a selected language.
 *
 * @param <E> the effective entity type used to represent code list entries
 * @param <Q> the effective type of the query
 */
public class CodeListExportJob<E extends BaseEntity<?> & CodeListEntry<?, ?, ?>, Q extends Query<Q, E, ?>>
        extends EntityExportJob<E, Q> {
    private static final String CLE_VALUE = CodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.VALUE).getName();
    private static final String CLE_ADDITIONAL_VALUE =
            CodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.ADDITIONAL_VALUE).getName();
    private static final String CLE_DESCRIPTION =
            CodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.DESCRIPTION).getName();

    private String languagePrefix;

    private final Optional<String> languageParameter;
    private final CodeList codeList;

    /**
     * Creates a new job for the given factory, name and process.
     *
     * @param templateFileParameter the parameter which is used to select the template file to use
     * @param destinationParameter  the parameter used to select the destination for the file being written
     * @param fileTypeParameter     the file type to use when writing the line based data
     * @param type                  the type of entities being imported
     * @param dictionary            the export dictionary to use
     * @param defaultMapping        the default mapping (default column order) to use
     * @param process               the process context itself
     * @param factoryName           the name of the factory which created this job
     * @param languageParameter     the parameter which specifies the language for which the CodeList should be exported
     * @param codeList              the target CodeList
     */
    @SuppressWarnings("squid:S00107")
    @Explain("We rather have 9 parameters here and keep the logic properly encapsulated")
    public CodeListExportJob(FileParameter templateFileParameter,
                             FileOrDirectoryParameter destinationParameter,
                             EnumParameter<ExportFileType> fileTypeParameter,
                             Class<E> type,
                             ImportDictionary dictionary,
                             List<String> defaultMapping,
                             ProcessContext process,
                             String factoryName,
                             Optional<String> languageParameter,
                             CodeList codeList) {
        super(templateFileParameter,
              destinationParameter,
              fileTypeParameter,
              type,
              dictionary,
              defaultMapping,
              process,
              factoryName);
        this.codeList = codeList;
        this.languageParameter = languageParameter;
        languageParameter.ifPresent(lang -> {
            this.languagePrefix = lang + "_";
            this.defaultMapping.add(languagePrefix + CLE_VALUE);
            this.defaultMapping.add(languagePrefix + CLE_ADDITIONAL_VALUE);
            this.defaultMapping.add(languagePrefix + CLE_DESCRIPTION);

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
    public void execute() throws Exception {
        process.addLink(new ProcessLink().withLabel("$CodeList").withUri("/code-list/" + codeList.getIdAsString()));
        process.updateTitle(Strings.apply("%s - %s", process.getTitle(), codeList.getCodeListData().getName()));
        super.execute();
    }

    @Nullable
    @Override
    protected Function<E, Object> customFieldExtractor(String field) {
        if (!field.startsWith(languagePrefix) || !languageParameter.isPresent()) {
            return null;
        }

        String realField = field.replaceFirst(languagePrefix, "");
        if (CLE_VALUE.equals(realField)
            || CLE_ADDITIONAL_VALUE.equals(realField)
            || CLE_DESCRIPTION.equals(realField)) {
            return codeListEntry -> codeListEntry.getTranslations()
                                                 .getText(Mapping.named(realField), languageParameter.get())
                                                 .orElse(null);
        }

        return null;
    }
}
