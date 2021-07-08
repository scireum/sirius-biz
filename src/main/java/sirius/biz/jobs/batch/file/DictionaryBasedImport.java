/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.importer.format.ImportDictionary;
import sirius.biz.jobs.params.BooleanParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ErrorContext;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Callback;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Values;
import sirius.kernel.commons.Watch;
import sirius.kernel.health.HandledException;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;

/**
 * Provides a helper to process line based values using a {@link ImportDictionary}.
 * <p>
 * This essentially maps a {@link Values} to a {@link Context} for each row.
 */
public class DictionaryBasedImport {

    /**
     * Contains the parameter which is used to determine if empty values should be ignored.
     */
    public static final Parameter<Boolean> IGNORE_EMPTY_PARAMETER =
            new BooleanParameter("ignoreEmpty", "$DictionaryBasedImportJobFactory.ignoreEmpty").withDescription(
                    "$DictionaryBasedImportJobFactory.ignoreEmpty.help").build();

    protected final ProcessContext process;
    protected final ImportDictionary dictionary;
    protected final String filename;
    protected Callback<Tuple<Integer, Context>> rowHandler;
    protected final ErrorContext errorContext;

    protected boolean ignoreEmptyValues;
    protected String rowCounterName = "$LineBasedJob.row";

    /**
     * Creates a new job for the given factory, name and process.
     *
     * @param dictionary the import dictionary to use
     * @param process    the process context itself
     */
    protected DictionaryBasedImport(String filename,
                                    ImportDictionary dictionary,
                                    ProcessContext process,
                                    Callback<Tuple<Integer, Context>> rowHandler) {
        this.filename = filename;
        this.dictionary = dictionary;
        this.process = process;
        this.rowHandler = rowHandler;
        this.errorContext = ErrorContext.get();
    }

    protected DictionaryBasedImport withRowCounterName(@Nullable String rowCounterName) {
        if (Strings.isFilled(rowCounterName)) {
            this.rowCounterName = rowCounterName;
        }
        return this;
    }

    protected DictionaryBasedImport withIgnoreEmptyValues(boolean ignoreEmptyValues) {
        this.ignoreEmptyValues = ignoreEmptyValues;
        return this;
    }

    /**
     * Invoked for each row in an import file.
     *
     * @param index the line number to process
     * @param row   the data to process
     */
    public void handleRow(int index, Values row) {
        if (index == 1) {
            handleHeaderRow(row);
        } else {
            handleDataRow(index, row);
        }
    }

    private void handleHeaderRow(Values row) {
        if (dictionary.hasIdentityMapping()) {
            boolean problemsFound = dictionary.detectHeaderProblems(row, (problem, isFatal) -> {
                if (Boolean.TRUE.equals(isFatal)) {
                    process.log(ProcessLog.error().withMessage(errorContext.enhanceMessage(problem)));
                } else {
                    process.log(ProcessLog.warn().withMessage(errorContext.enhanceMessage(problem)));
                }
            }, true);

            if (problemsFound) {
                TaskContext.get().cancel();
            }
        } else {
            dictionary.resetMappings();
            try {
                dictionary.determineMappingFromHeadings(row, false);
                process.log(ProcessLog.info().withMessage(dictionary.getMappingAsString()));
            } catch (HandledException e) {
                process.log(ProcessLog.error().withMessage(errorContext.enhanceMessage(e.getMessage())));
                TaskContext.get().cancel();
            }
        }
    }

    private void handleDataRow(int index, Values row) {
        if (row.length() == 0) {
            return;
        }

        Context context = filterEmptyValues(dictionary.load(row, false));
        if (!isEmptyContext(context)) {
            Watch watch = Watch.start();
            errorContext.perform(() -> {
                try {
                    rowHandler.invoke(Tuple.create(index, context));
                } catch (Exception exception) {
                    process.incCounter(NLS.get("LineBasedJob.erroneousRow"));
                    throw exception;
                }
            });
            process.addTiming(NLS.smartGet(rowCounterName), watch.elapsedMillis());
        }
    }

    protected Context filterEmptyValues(Context source) {
        if (ignoreEmptyValues) {
            return source.removeEmpty();
        }

        return source;
    }

    protected boolean isEmptyContext(Context context) {
        return context.entrySet().stream().noneMatch(entry -> Strings.isFilled(entry.getValue()));
    }
}
