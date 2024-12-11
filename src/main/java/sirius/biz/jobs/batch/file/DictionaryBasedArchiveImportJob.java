/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.importer.format.ImportDictionary;
import sirius.biz.process.ErrorContext;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.util.ExtractedFile;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Callback;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.UnitOfWork;
import sirius.web.data.LineBasedProcessor;

import javax.annotation.CheckReturnValue;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Provides an import job which allows importing dictionary-based data from multiple archived files in a specific order.
 */
public abstract class DictionaryBasedArchiveImportJob extends ArchiveImportJob {

    protected static final String ERROR_CONTEXT_FILE_PATH = "$DictionaryBasedArchiveImportJob.file";
    protected static final String ERROR_CONTEXT_ROW = "$LineBasedJob.row";

    /**
     * Describes a file to be imported from an archive.
     */
    public static class ImportFile {
        protected String filename;
        protected ImportDictionary dictionary;
        protected boolean required;
        protected boolean ignoreEmptyFields;
        protected Callback<Tuple<Integer, Context>> rowHandler;
        protected UnitOfWork completionHandler;
        protected String rowCounterName;

        /**
         * Creates a new instance for the given name.
         *
         * @param filename   the name of the file to import
         * @param dictionary the import dictionary to use
         * @param rowHandler the row handler used to process each row
         */
        public ImportFile(String filename, ImportDictionary dictionary, Callback<Tuple<Integer, Context>> rowHandler) {
            this.filename = filename;
            this.dictionary = dictionary;
            this.rowHandler = rowHandler;
        }

        /**
         * Creates a new instance for the given name.
         *
         * @param filename   the name of the file to import
         * @param rowHandler the row handler used to process each row
         */
        public ImportFile(String filename, Callback<Tuple<Integer, Context>> rowHandler) {
            this.filename = filename;
            this.rowHandler = rowHandler;
        }

        /**
         * Sets a callback handler to be called once the file has been processed.
         *
         * @param completionHandler the handler to be called
         * @return the file itself for fluent method calls
         */
        public ImportFile withCompletionHandler(UnitOfWork completionHandler) {
            this.completionHandler = completionHandler;
            return this;
        }

        /**
         * Marks the file as required.
         *
         * @return the file itself for fluent method calls
         */
        @CheckReturnValue
        public ImportFile markRequired() {
            this.required = true;
            return this;
        }

        /**
         * Ignores empty fields instead of forcing <tt>null</tt> values there.
         *
         * @return the file itself for fluent method calls
         */
        @CheckReturnValue
        public ImportFile ignoreEmptyFields() {
            this.ignoreEmptyFields = true;
            return this;
        }

        /**
         * Specifies a custom counter name to be used for each processed row.
         *
         * @param rowCounterName the name of the counter which will be smart translated using
         *                       {@link sirius.kernel.nls.NLS#smartGet(String)}.
         * @return the file itself for fluent method calls
         */
        @CheckReturnValue
        public ImportFile withRowCounterName(String rowCounterName) {
            this.rowCounterName = rowCounterName;
            return this;
        }

        public String getFilename() {
            return filename;
        }
    }

    private final Set<String> handledFiles = new HashSet<>();

    /**
     * Creates a new job for the given process context.
     *
     * @param process the process context in which the job is executed
     */
    protected DictionaryBasedArchiveImportJob(ProcessContext process) {
        super(process);
    }

    /**
     * Collects all import files and their desired order of processing.
     * <p>
     * Note that the same file can occur several times if multiple passes are required.
     *
     * @param importFiles the collector to be supplied with all files to import
     */
    protected abstract void collectImportFiles(Consumer<ImportFile> importFiles);

    /**
     * Provides a list of stages, each collecting a different set of files.
     * <p>
     * This method should only be overwritten when the import requires a complex handling.
     * For instance: based on the contents of a specific file, different sets of files
     * should be imported afterwards.
     *
     * @return list of import file consumers. Defaults to {@link #collectImportFiles(Consumer)}
     */
    protected List<Consumer<Consumer<ImportFile>>> fetchStages() {
        return Collections.singletonList(this::collectImportFiles);
    }

    @Override
    protected void importEntries() throws Exception {
        handledFiles.clear();

        for (Consumer<Consumer<ImportFile>> fileCollector : fetchStages()) {
            List<ImportFile> importFiles = new ArrayList<>();
            fileCollector.accept(importFiles::add);

            for (ImportFile importFile : importFiles) {
                if (!TaskContext.get().isActive()) {
                    return;
                }
                Optional<ExtractedFile> extractedFile = fetchEntry(importFile.filename);
                if (extractedFile.isPresent()) {
                    handleFile(importFile, extractedFile.get());
                } else if (!handledFiles.contains(importFile.filename)) {
                    handleMissingFile(importFile.filename, importFile.required);
                }

                if(!(Files.isConsideredHidden(importFile.filename) || Files.isConsideredMetadata(importFile.filename))) {
                    handledFiles.add(importFile.filename);
                }
            }
        }

        if (!TaskContext.get().isActive()) {
            return;
        }
        extractAllFiles(file -> {
            if (!handledFiles.contains(file.getFilePath())) {
                handleAuxiliaryFile(file);
            }
        });
    }

    protected void handleFile(ImportFile importFile, ExtractedFile extractedFile) throws Exception {
        process.log(ProcessLog.info()
                              .withNLSKey("DictionaryBasedArchiveImportJob.msgImportFile")
                              .withContext("file", importFile.filename));
        DictionaryBasedImport dictionaryBasedImport = new DictionaryBasedImport(importFile.filename,
                                                                                importFile.dictionary,
                                                                                process,
                                                                                importFile.rowHandler).withIgnoreEmptyValues(
                importFile.ignoreEmptyFields).withRowCounterName(importFile.rowCounterName);

        ErrorContext.get().withContext(ERROR_CONTEXT_FILE_PATH, extractedFile.getFilePath());
        try (InputStream stream = extractedFile.openInputStream()) {
            LineBasedProcessor.create(importFile.filename, stream, false).run((lineNumber, row) -> {
                ErrorContext.get().withContext(ERROR_CONTEXT_ROW, lineNumber);
                dictionaryBasedImport.handleRow(lineNumber, row);
                ErrorContext.get().removeContext(ERROR_CONTEXT_ROW);
            }, error -> {
                process.handle(error);
                ErrorContext.get().removeContext(ERROR_CONTEXT_ROW);
                return true;
            });
        } finally {
            ErrorContext.get().removeContext(ERROR_CONTEXT_FILE_PATH);
        }

        if (importFile.completionHandler != null) {
            importFile.completionHandler.execute();
        }
    }
}
