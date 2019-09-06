/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.importer.Importer;
import sirius.biz.importer.format.FieldDefinition;
import sirius.biz.importer.format.ImportDictionary;
import sirius.biz.jobs.params.EnumParameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.storage.layer3.FileOrDirectoryParameter;
import sirius.biz.storage.layer3.FileParameter;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.biz.tenants.Tenants;
import sirius.biz.web.TenantAware;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;
import sirius.db.mixing.query.Query;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Values;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.web.data.LineBasedProcessor;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provides a job for exporting entities as line based files (CSV, Excel) via a {@link EntityExportJobFactory}.
 * <p>
 * Utilizing {@link sirius.biz.importer.ImportHandler import handlers} this can be used as is in most cases. However
 * a subclass overwriting {@link #customFieldLookup} or {@link #customFieldExtractor(String)} might be required to
 * perform some mappings. To control which entities should be exported {@link #createFullExportQuery()} has to be
 * overwritten.
 * <p>
 * that this job can operate in three modes:
 * <ol>
 *     <li>Simply export all matching entities using the standard mapping (column order)</li>
 *     <li>Use a template file which declares the expected column order (via the first non-empty line)</li>
 *     <li>Populate/enhance a template file which contains partially filled rows</li>
 * </ol>
 *
 * @param <E> the type of entities being exported by this job
 */
public abstract class EntityExportJob<E extends BaseEntity<?>> extends LineBasedExportJob {

    protected final VirtualFile templateFile;
    protected final ImportDictionary dictionary;
    protected final EntityDescriptor descriptor;
    protected final Importer importer;
    protected final List<String> defaultMapping;
    protected Class<E> type;
    protected List<Function<E, Object>> extractors;

    @Part
    private static Mixing mixing;

    @Part
    private static Tenants<?, ?, ?> tenants;

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
     */
    protected EntityExportJob(FileParameter templateFileParameter,
                              FileOrDirectoryParameter destinationParameter,
                              EnumParameter<ExportFileType> fileTypeParameter,
                              Class<E> type,
                              ImportDictionary dictionary,
                              List<String> defaultMapping,
                              ProcessContext process) {
        super(destinationParameter, fileTypeParameter, process);
        this.defaultMapping = new ArrayList<>(defaultMapping);
        this.templateFile = process.getParameter(templateFileParameter).orElse(null);
        this.dictionary = dictionary.withCustomFieldLookup(this::customFieldLookup);
        this.type = type;
        this.descriptor = mixing.getDescriptor(type);
        this.importer = new Importer(process.getTitle());
    }

    /**
     * Resolves custom fields which are not known by the entity or its {@link sirius.biz.importer.ImportHandler}.
     *
     * @param field the field to resolve
     * @return a proper field description or <tt>null</tt> if the field is unknown
     */
    @Nullable
    protected FieldDefinition customFieldLookup(String field) {
        return null;
    }

    /**
     * Creates a custom extractor for the given field.
     *
     * @param field the field to extract
     * @return the extractor to use or <tt>null</tt> to use the default extractor
     */
    @Nullable
    protected Function<E, Object> customFieldExtractor(String field) {
        return null;
    }

    @Override
    protected String determineFilenameWithoutExtension() {
        return descriptor.getPluralLabel();
    }

    @Override
    protected String determineFileExtension() {
        return fileType.name().toLowerCase();
    }

    @Override
    protected void executeIntoExport() throws Exception {
        if (templateFile == null) {
            fullExportWithoutTemplate();
        } else {
            templateBasedExport();
        }
    }

    /**
     * Uses the provided template file to determine which columns should be exported.
     * <p>
     * If the template file contains more than one row, this will enhance all rows, otherwise all matching entities will
     * be exported via {@link #fullExportWithGivenMapping()}.
     *
     * @throws Exception in case or a severe problem which should abort the job
     */
    private void templateBasedExport() throws Exception {
        AtomicBoolean seenTemplateRow = new AtomicBoolean(false);
        LineBasedProcessor.create(templateFile.name(), templateFile.createInputStream()).run((rowNumber, row) -> {
            if (row.length() == 0) {
                return;
            }

            if (!dictionary.hasMappings()) {
                determineMappingsFromRow(rowNumber, row);
            } else {
                if (!seenTemplateRow.get()) {
                    process.log(ProcessLog.info().withNLSKey("EntityExportJob.startingTemplateBasedExport"));
                    seenTemplateRow.set(true);
                }
                handleTemplateRow(rowNumber, row);
            }
        }, error -> {
            process.handle(error);
            return true;
        });

        if (!seenTemplateRow.get()) {
            fullExportWithGivenMapping();
        }
    }

    /**
     * Determines the mappings / columns to export by parsing the given row.
     *
     * @param rowNumber the row number which is used
     * @param row       the row which contains the column names
     */
    private void determineMappingsFromRow(int rowNumber, Values row) {
        process.log(ProcessLog.info().withNLSKey("EntityExportJob.learningMapping").withContext("row", rowNumber));
        dictionary.determineMappingFromHeadings(row, false);
        process.log(ProcessLog.info().withMessage(dictionary.getMappingAsString()));
        setupExtractors();
        try {
            export.addRow(row.asList());
        } catch (Exception e) {
            process.handle(Exceptions.handle()
                                     .to(Log.BACKGROUND)
                                     .error(e)
                                     .withNLSKey("LineBasedJob.failureInRow")
                                     .set("row", rowNumber)
                                     .handle());
        }
    }

    /**
     * Creates an extractor for each known column if the effective mapping.
     */
    private void setupExtractors() {
        extractors = dictionary.getMappings().stream().map(mapping -> {
            if (mapping == null) {
                return null;
            }

            return createExtractor(mapping);
        }).collect(Collectors.toList());
    }

    /**
     * Creates the effective extractor for the given field.
     *
     * @param field the field to extract
     * @return the extractor to use
     */
    protected Function<E, Object> createExtractor(String field) {
        Function<E, Object> customExtractor = customFieldExtractor(field);
        if (customExtractor != null) {
            return customExtractor;
        }

        return importer.findHandler(type).createExtractor(field);
    }

    /**
     * Enhances a data row read from the template.
     *
     * @param rowNumber the number of the row being handled
     * @param row       the row data to process
     */
    private void handleTemplateRow(int rowNumber, Values row) {
        Watch w = Watch.start();
        try {
            Context data = dictionary.load(row, false);
            Optional<E> entity = importer.tryFind(type, data);
            if (entity.isPresent()) {
                export.addRow(exportAsRow(row, entity.get()));
            } else {
                export.addRow(row.asList());
            }
        } catch (HandledException e) {
            process.handle(Exceptions.createHandled()
                                     .withNLSKey("LineBasedJob.errorInRow")
                                     .set("row", rowNumber)
                                     .set("message", e.getMessage())
                                     .handle());
        } catch (Exception e) {
            process.handle(Exceptions.handle()
                                     .to(Log.BACKGROUND)
                                     .error(e)
                                     .withNLSKey("LineBasedJob.failureInRow")
                                     .set("row", rowNumber)
                                     .handle());
        } finally {
            process.addTiming(descriptor.getPluralLabel(), w.elapsedMillis());
        }
    }

    /**
     * Applies all extractors to transform an entity into the target row.
     *
     * @param baseRow the base row (for a template based export) to fill unmapped fields
     * @param entity  the entity to export
     * @return a fully populated row representing the entity
     */
    private List<Object> exportAsRow(Values baseRow, E entity) {
        List<Object> row = new ArrayList<>(extractors.size());
        for (int i = 0; i < extractors.size(); i++) {
            Function<E, Object> extractor = extractors.get(i);
            if (extractor == null) {
                row.add(baseRow == null ? null : baseRow.at(i));
            } else {
                row.add(extractor.apply(entity));
            }
        }

        return row;
    }

    /**
     * Performs a full export without any template.
     *
     * @throws Exception in case or a severe problem which should abort the job
     */
    private void fullExportWithoutTemplate() throws Exception {
        process.log(ProcessLog.info().withNLSKey("EntityExportJob.exportWithDefaultMapping"));
        dictionary.useMapping(defaultMapping);
        setupExtractors();
        export.addRow(dictionary.getMappings().stream().map(dictionary::expandToLabel).collect(Collectors.toList()));

        fullExportWithGivenMapping();
    }

    /**
     * Actually exports all entities using the mapping which has already been determined.
     * <p>
     * The mapping was either determined by {@link #templateBasedExport()} or by using the default mapping in
     * {@link #fullExportWithoutTemplate()}.
     */
    private void fullExportWithGivenMapping() {
        process.log(ProcessLog.info().withNLSKey("EntityExport.fullExport"));
        createFullExportQuery().iterateAll(entity -> {
            Watch watch = Watch.start();
            try {
                export.addRow(exportAsRow(null, entity));
            } catch (IOException e) {
                throw process.handle(e);
            } finally {
                process.addTiming(descriptor.getPluralLabel(), watch.elapsedMillis());
            }
        });
    }

    /**
     * Creates the query which selects all entities to export.
     *
     * @return the query which yields all entities to export
     */
    @SuppressWarnings("unchecked")
    protected Query<?, E, ?> createFullExportQuery() {
        Query<?, E, ?> query = (Query<?, E, ?>) descriptor.getMapper().select(type);

        if (TenantAware.class.isAssignableFrom(type)) {
            query.eq(TenantAware.TENANT, tenants.getRequiredTenant());
        }

        return query;
    }

    @Override
    public void close() throws IOException {
        try {
            if (importer.getContext().hasBatchContext()) {
                process.log(ProcessLog.info()
                                      .withMessage(importer.getContext().getBatchContext().toString())
                                      .asSystemMessage());
            }
            this.importer.close();
        } catch (IOException e) {
            process.handle(e);
        }
        super.close();
    }
}
