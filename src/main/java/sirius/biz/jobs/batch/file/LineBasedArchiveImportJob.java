/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.importer.AfterLineLoadEvent;
import sirius.biz.process.ErrorContext;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.util.ExtractedFile;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Values;
import sirius.kernel.commons.Watch;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.data.LineBasedProcessor;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides an import job which allows importing line-based data from multiple archived files in a specific order.
 */
public abstract class LineBasedArchiveImportJob extends DictionaryBasedArchiveImportJob {

    /**
     * Creates a new job for the given process context.
     *
     * @param process the process context in which the job is executed
     */
    protected LineBasedArchiveImportJob(ProcessContext process) {
        super(process);
    }

    @Override
    protected void handleFile(ImportFile importFile, ExtractedFile extractedFile) throws Exception {
        process.log(ProcessLog.info()
                              .withNLSKey("DictionaryBasedArchiveImportJob.msgImportFile")
                              .withContext("file", importFile.filename));
        ErrorContext.get().withContext(ERROR_CONTEXT_FILE_PATH, extractedFile.getFilePath());
        try (InputStream stream = extractedFile.openInputStream()) {
            Map<Integer, String> columnNames = new HashMap<>();
            LineBasedProcessor.create(importFile.filename, stream, false).run((lineNumber, row) -> {
                ErrorContext.get().handleInContext(ERROR_CONTEXT_ROW, lineNumber, () -> {
                    if (lineNumber == 1) {
                        handleHeaderRow(row, columnNames, importFile);
                    } else {
                        handleRow(row, lineNumber, columnNames, importFile);
                    }
                });
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

    /**
     * Normalizes how columns are exposed in each row's context.
     * <p>
     * By default, this method returns the column name as read from the input file.
     * Use this method to normalize headers, such as converting them to lower case,
     * replacing characters or translating aliases into expected names.
     *
     * @param header the original header name
     * @return the modified header name
     */
    protected String normalizeHeader(String header) {
        return header;
    }

    private void handleHeaderRow(Values row, Map<Integer, String> columnNames, ImportFile importFile) {
        for (int index = 0; index < row.length(); index++) {
            String headerName = normalizeHeader(row.at(index).asString());
            if (columnNames.containsValue(headerName)) {
                columnNames.clear();
                if (importFile.required) {
                    TaskContext.get().cancel();
                }
                throw Exceptions.createHandled()
                                .withNLSKey("LineBasedArchiveImportJob.duplicateColumn")
                                .set("column", headerName)
                                .handle();
            }
            columnNames.put(index, headerName);
        }
    }

    private void handleRow(Values row, int line, Map<Integer, String> columnNames, ImportFile importFile)
            throws Exception {
        if (row.length() == 0 || columnNames.isEmpty()) {
            return;
        }

        if (row.length() > columnNames.size()) {
            process.incCounter("LineBasedJob.erroneousRow");
            throw Exceptions.createHandled()
                            .withNLSKey("ImportDictionary.tooManyColumns")
                            .set("count", row.length())
                            .set("columns", columnNames.size())
                            .handle();
        }

        Watch watch = Watch.start();
        Context context = Context.create();
        for (int index = 0; index < row.length(); index++) {
            context.put(columnNames.get(index), row.at(index).getRawString());
        }

        try {
            if (importer.getContext().getEventHandler().isActive()) {
                AfterLineLoadEvent event = new AfterLineLoadEvent(context, importer.getContext());
                importer.getContext().getEventHandler().handleEvent(event);
            }
            importFile.rowHandler.invoke(Tuple.create(line, context));
        } catch (Exception exception) {
            process.incCounter("LineBasedJob.erroneousRow");
            throw exception;
        }
        process.addTiming(NLS.smartGet(importFile.rowCounterName), watch.elapsedMillis());
    }
}
