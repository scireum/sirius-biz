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
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Callback;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Values;
import sirius.kernel.commons.Watch;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;

/**
 * Provides a helper to process line based values using a {@link ImportDictionary}.
 * <p>
 * This essentially maps a {@link Values} to a {@link Context} for each row.
 */
public class DictionaryBasedImport {

    private static final String MESSAGE_KEY = "message";
    private static final String ERROR_IN_ROW_KEY = "LineBasedJob.errorInRow";

    /**
     * Contains the parameter which is used to determine if empty values should be ignored.
     */
    public static final Parameter<Boolean> IGNORE_EMPTY_PARAMETER =
            new BooleanParameter("ignoreEmpty", "$DictionaryBasedImportJobFactory.ignoreEmpty").withDescription(
                    "$DictionaryBasedImportJobFactory.ignoreEmpty.help").build();

    protected final ProcessContext process;
    protected final boolean ignoreEmptyValues;
    protected final ImportDictionary dictionary;
    protected Callback<Tuple<Integer, Context>> rowHandler;

    /**
     * Creates a new job for the given factory, name and process.
     *
     * @param dictionary the import dictionary to use
     * @param process    the process context itself
     */
    protected DictionaryBasedImport(ImportDictionary dictionary,
                                    ProcessContext process,
                                    boolean ignoreEmptyValues,
                                    Callback<Tuple<Integer, Context>> rowHandler) {

        this.dictionary = dictionary;
        this.process = process;
        this.ignoreEmptyValues = ignoreEmptyValues;
        this.rowHandler = rowHandler;
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
                    process.log(ProcessLog.error()
                                          .withNLSKey(ERROR_IN_ROW_KEY)
                                          .withContext("row", 1)
                                          .withContext(MESSAGE_KEY, problem));
                } else {
                    process.log(ProcessLog.warn()
                                          .withNLSKey(ERROR_IN_ROW_KEY)
                                          .withContext("row", 1)
                                          .withContext(MESSAGE_KEY, problem));
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
                process.log(ProcessLog.error()
                                      .withNLSKey(ERROR_IN_ROW_KEY)
                                      .withContext("row", 1)
                                      .withContext(MESSAGE_KEY, e.getMessage()));
                TaskContext.get().cancel();
            }
        }
    }

    private void handleDataRow(int index, Values row) {
        if (row.length() == 0) {
            return;
        }

        Watch watch = Watch.start();
        try {
            Context context = filterEmptyValues(dictionary.load(row, false));
            if (!isEmptyContext(context)) {
                rowHandler.invoke(Tuple.create(index, context));
            }
        } catch (HandledException e) {
            process.incCounter(NLS.get("LineBasedJob.erroneousRow"));
            process.handle(Exceptions.createHandled()
                                     .to(Log.BACKGROUND)
                                     .withNLSKey(ERROR_IN_ROW_KEY)
                                     .set("row", index)
                                     .set(MESSAGE_KEY, e.getMessage())
                                     .handle());
        } catch (Exception e) {
            process.incCounter(NLS.get("LineBasedJob.erroneousRow"));
            process.handle(Exceptions.handle()
                                     .to(Log.BACKGROUND)
                                     .error(e)
                                     .withNLSKey("LineBasedJob.failureInRow")
                                     .set("row", index)
                                     .handle());
        } finally {
            process.addTiming(NLS.get("LineBasedJob.row"), watch.elapsedMillis());
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
