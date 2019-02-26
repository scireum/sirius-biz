/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.importer.ImportDictionary;
import sirius.biz.importer.LineBasedAliases;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Values;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.web.data.LineBasedProcessor;
import sirius.web.data.RowProcessor;

import java.io.InputStream;

/**
 * Provides a job for importing line based files (CSV, Excel) via a {@link LineBasedImportJobFactory}.
 * <p>
 * Utilizing {@link sirius.biz.importer.ImportHandler import handlers} this can be used as is in most cases. However
 * a subclass overwriting {@link #handleRow(int, Context)} might be required to perform some mappings.
 */
public class LineBasedImportJob extends FileImportJob implements RowProcessor {

    protected final ImportDictionary dictionary;
    protected final EntityDescriptor descriptor;
    protected LineBasedAliases aliases;
    protected Class<? extends BaseEntity<?>> type;

    @Part
    private static Mixing mixing;

    /**
     * Creates a new job for the given type, name and process.
     *
     * @param type    the type of entities being imported
     * @param process the process context itself
     */
    public LineBasedImportJob(LineBasedImportJobFactory factory,
                              Class<? extends BaseEntity<?>> type,
                              ProcessContext process) {
        super(factory, process);
        this.dictionary = importer.getDictionary(type);
        this.type = type;
        this.descriptor = mixing.getDescriptor(type);
    }

    @Override
    protected void executeForStream(String filename, InputStream in) throws Exception {
        LineBasedProcessor.create(filename, in).run(this, error -> {
            process.handle(error);
            return true;
        });
    }

    @Override
    public void handleRow(int index, Values row) {
        if (aliases == null) {
            aliases = new LineBasedAliases(row, dictionary);
            process.log(ProcessLog.info().withMessage(aliases.toString()));
        } else {
            Watch w = Watch.start();
            try {
                Context ctx = aliases.transform(row);
                handleRow(index, ctx);
            } catch (Exception e) {
                process.handle(e);
            } finally {
                process.addTiming(descriptor.getPluralLabel(), w.elapsedMillis());
            }
        }
    }

    /**
     * Handles a single row of the import.
     *
     * @param index the index of the row being processed
     * @param ctx   the row represented as context
     */
    protected void handleRow(int index, Context ctx) {
        importer.createOrUpdateInBatch(importer.findAndLoad(type, ctx));
    }
}
