/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.importer.format.ImportDictionary;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.util.ExtractedFile;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Callback;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.UnitOfWork;
import sirius.web.data.LineBasedProcessor;

import javax.annotation.CheckReturnValue;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Provides an import job which allows to import line based data from multiple archived files in a specific order.
 */
public abstract class DictionaryBasedArchiveImportJob extends ArchiveImportJob {

    private static final String ERROR_CONTEXT_FILE_PATH = "$DictionaryBasedArchiveImportJob.file";
    private static final String ERROR_CONTEXT_ROW = "$LineBasedJob.row";

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

    @Override
    protected void importEntries() throws Exception {
        List<ImportFile> importFiles = new ArrayList<>();
        collectImportFiles(importFiles::add);
        handledFiles.clear();

        for (ImportFile importFile : importFiles) {
            if (!TaskContext.get().isActive()) {
                break;
            }
            Optional<ExtractedFile> extractedFile = fetchEntry(importFile.filename);
            if (extractedFile.isPresent()) {
                handleFile(importFile, extractedFile.get());
            } else if (!handledFiles.contains(importFile.filename)) {
                handleMissingFile(importFile.filename, importFile.required);
            }

            handledFiles.add(importFile.filename);
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

    private void handleFile(ImportFile importFile, ExtractedFile extractedFile) throws Exception {
        process.log(ProcessLog.info()
                              .withNLSKey("DictionaryBasedArchiveImportJob.msgImportFile")
                              .withContext("file", importFile.filename));
        DictionaryBasedImport dictionaryBasedImport = new DictionaryBasedImport(importFile.filename,
                                                                                importFile.dictionary,
                                                                                process,
                                                                                importFile.rowHandler).withIgnoreEmptyValues(
                importFile.ignoreEmptyFields).withRowCounterName(importFile.rowCounterName);

        errorContext.withContext(ERROR_CONTEXT_FILE_PATH, extractedFile.getFilePath());
        try (InputStream stream = extractedFile.openInputStream()) {
            LineBasedProcessor.create(importFile.filename, stream, false).run((lineNumber, row) -> {
                errorContext.withContext(ERROR_CONTEXT_ROW, lineNumber);
                dictionaryBasedImport.handleRow(lineNumber, row);
                errorContext.removeContext(ERROR_CONTEXT_ROW);
            }, error -> {
                process.handle(error);
                errorContext.removeContext(ERROR_CONTEXT_ROW);
                return true;
            });
        } finally {
            errorContext.removeContext(ERROR_CONTEXT_FILE_PATH);
        }

        if (importFile.completionHandler != null) {
            importFile.completionHandler.execute();
        }
    }
}
