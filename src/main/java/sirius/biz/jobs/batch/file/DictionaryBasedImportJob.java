/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.importer.format.FieldDefinition;
import sirius.biz.importer.format.ImportDictionary;
import sirius.biz.jobs.params.BooleanParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Values;
import sirius.kernel.commons.Watch;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;

/**
 * Provides a job for importing line based files (CSV, Excel) which utilizes a {@link ImportDictionary} to map colums
 * to fields.
 */
public abstract class DictionaryBasedImportJob extends LineBasedImportJob {

    /**
     * Contains the parameter which is used to determine if empty values should be ignored.
     */
    public static final Parameter<Boolean> IGNORE_EMPTY_PARAMETER =
            new BooleanParameter("ignoreEmpty", "$DictionaryBasedImportJobFactory.ignoreEmpty").withDescription(
                    "$DictionaryBasedImportJobFactory.ignoreEmpty.help").build();

    protected boolean ignoreEmptyValues;
    protected ImportDictionary dictionary;
    protected boolean skipLoggingMappings;
    protected boolean validateHeader;

    /**
     * Creates a new job for the given factory, name and process.
     *
     * @param dictionary the import dictionary to use
     * @param process    the process context itself
     */
    protected DictionaryBasedImportJob(ImportDictionary dictionary, ProcessContext process) {
        super(process);
        this.ignoreEmptyValues = process.getParameter(IGNORE_EMPTY_PARAMETER).orElse(false);
        if (dictionary != null) {
            this.dictionary = dictionary.withCustomFieldLookup(this::customFieldLookup);
        }
    }

    /**
     * Suppress logging the mappings determined from the dictionary.
     *
     * @return the object itself for fluent calls
     */
    public DictionaryBasedImportJob suppressLoggingMappings() {
        skipLoggingMappings = true;
        return this;
    }

    /**
     * Validates the header of the input file against the dictionary.
     *
     * @return the object itself for fluent calls
     */
    public DictionaryBasedImportJob validateHeader() {
        validateHeader = true;
        return this;
    }

    @Nullable
    protected FieldDefinition customFieldLookup(String field) {
        return null;
    }

    @Override
    public void handleRow(int index, Values row) {
        if (index == 1) {
            dictionary = determineDictionary();
            dictionary.resetMappings();
        }

        if (row.length() == 0) {
            return;
        }

        if (!dictionary.hasMappings()) {
            dictionary.determineMappingFromHeadings(row, validateHeader);
            if (!skipLoggingMappings) {
                process.log(ProcessLog.info().withMessage(dictionary.getMappingAsString()));
            }
        } else {
            Watch watch = Watch.start();
            try {
                Context context = filterEmptyValues(dictionary.load(row, false));
                if (!isEmptyContext(context)) {
                    handleRow(index, context);
                }
            } catch (HandledException e) {
                process.incCounter(NLS.get("LineBasedJob.erroneousRow"));
                process.handle(Exceptions.createHandled()
                                         .to(Log.BACKGROUND)
                                         .withNLSKey("LineBasedJob.errorInRow")
                                         .set("row", index)
                                         .set("message", e.getMessage())
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

    /**
     * Determines the dictionary used when processing the input file.
     * <p>
     * By default the dictionary used during initialization is used.
     * One can override this method to dynamically provide other dictionaries, for example
     * when processing entries of an archive.
     *
     * @return the dictionary used to handle the row
     */
    protected ImportDictionary determineDictionary() {
        return dictionary;
    }

    /**
     * Handles a single row of the import.
     *
     * @param index   the index of the row being processed
     * @param context the row represented as context
     */
    protected abstract void handleRow(int index, Context context);
}
