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
import sirius.biz.process.ProcessContext;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Values;

import javax.annotation.Nullable;

/**
 * Provides a job for importing line based files (CSV, Excel) which utilizes a {@link ImportDictionary} to map colums
 * to fields.
 */
public abstract class DictionaryBasedImportJob extends LineBasedImportJob {

    protected DictionaryBasedImport dictionaryBasedImport;

    /**
     * Creates a new job for the given factory, name and process.
     *
     * @param dictionary the import dictionary to use
     * @param process    the process context itself
     */
    protected DictionaryBasedImportJob(ImportDictionary dictionary, ProcessContext process) {
        super(process);
        this.dictionaryBasedImport = new DictionaryBasedImport(dictionary,
                                                               process,
                                                               process.getParameter(DictionaryBasedImport.IGNORE_EMPTY_PARAMETER)
                                                                      .orElse(false),
                                                               indexAndRow -> handleRow(indexAndRow.getFirst(),
                                                                                        indexAndRow.getSecond()));
    }

    @Nullable
    protected FieldDefinition customFieldLookup(String field) {
        return null;
    }

    @Override
    public void handleRow(int index, Values values) {
        dictionaryBasedImport.handleRow(index, values);
    }

    /**
     * Handles a single row of the import.
     *
     * @param index   the index of the row being processed
     * @param context the row represented as context
     */
    protected abstract void handleRow(int index, Context context);
}
