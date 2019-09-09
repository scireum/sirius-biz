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
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.storage.layer3.FileParameter;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Values;
import sirius.kernel.commons.Watch;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.io.InputStream;

/**
 * Provides a job for importing line based files (CSV, Excel) which utilizes a {@link ImportDictionary} to map colums
 * to fields.
 */
public abstract class DictionaryBasedImportJob extends LineBasedImportJob {

    protected boolean ignoreEmptyValues;
    protected final ImportDictionary dictionary;

    /**
     * Creates a new job for the given factory, name and process.
     *
     * @param fileParameter        the parameter which is used to derive the import file from
     * @param ignoreEmptyParameter the parameter which is used to determine if empty values should be ignored
     * @param dictionary           the import dictionary to use
     * @param process              the process context itself
     */
    protected DictionaryBasedImportJob(FileParameter fileParameter,
                                       BooleanParameter ignoreEmptyParameter,
                                       ImportDictionary dictionary,
                                       ProcessContext process) {
        super(fileParameter, process);
        this.ignoreEmptyValues = process.getParameter(ignoreEmptyParameter).orElse(false);
        this.dictionary = dictionary.withCustomFieldLookup(this::customFieldLookup);
    }

    @Nullable
    protected FieldDefinition customFieldLookup(String field) {
        return null;
    }

    @Override
    protected void executeForStream(String filename, InputStream in) throws Exception {
        dictionary.resetMappings();
        super.executeForStream(filename, in);
    }

    @Override
    public void handleRow(int index, Values row) {
        if (row.length() == 0) {
            return;
        }

        if (!dictionary.hasMappings()) {
            dictionary.determineMappingFromHeadings(row, false);
            process.log(ProcessLog.info().withMessage(dictionary.getMappingAsString()));
        } else {
            Watch watch = Watch.start();
            try {
                Context context = filterEmptyValues(dictionary.load(row, false));
                if (!isEmptyContext(context)) {
                    handleRow(index, context);
                }
            } catch (HandledException e) {
                process.handle(Exceptions.createHandled()
                                         .to(Log.BACKGROUND)
                                         .withNLSKey("LineBasedJob.errorInRow")
                                         .set("row", index)
                                         .set("message", e.getMessage())
                                         .handle());
            } catch (Exception e) {
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
     * Handles a single row of the import.
     *
     * @param index the index of the row being processed
     * @param context   the row represented as context
     */
    protected abstract void handleRow(int index, Context context);
}
