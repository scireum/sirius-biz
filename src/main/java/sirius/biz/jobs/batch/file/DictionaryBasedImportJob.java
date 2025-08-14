/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.importer.AfterLineLoadEvent;
import sirius.biz.importer.format.FieldDefinition;
import sirius.biz.importer.format.ImportDictionary;
import sirius.biz.process.ProcessContext;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Values;

import javax.annotation.Nullable;

/**
 * Provides a job for importing line based files (CSV, Excel) which utilizes a {@link ImportDictionary} to map columns
 * to fields.
 */
public abstract class DictionaryBasedImportJob extends LineBasedImportJob {

    protected final ImportDictionary dictionary;
    protected DictionaryBasedImport dictionaryBasedImport;

    /**
     * Creates a new job for the given factory, name and process.
     *
     * @param dictionary the import dictionary to use
     * @param process    the process context itself
     */
    protected DictionaryBasedImportJob(ImportDictionary dictionary, ProcessContext process) {
        super(process);

        this.dictionary = dictionary;
    }

    @Override
    public void execute() throws Exception {
        VirtualFile file = process.require(FILE_PARAMETER);
        this.dictionaryBasedImport =
                new DictionaryBasedImport(file.name(), dictionary, process, this::handleRow).withIgnoreEmptyValues(
                        process.getParameter(DictionaryBasedImport.IGNORE_EMPTY_PARAMETER).orElse(true));
        super.execute();
    }

    private void handleRow(Tuple<Integer, Context> indexAndRow) {
        if (importer.getContext().getEventHandler().isActive()) {
            AfterLineLoadEvent event = new AfterLineLoadEvent(indexAndRow.getSecond(), importer.getContext());
            importer.getContext().getEventHandler().handleEvent(event);
        }
        handleRow(indexAndRow.getFirst(), indexAndRow.getSecond());
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
