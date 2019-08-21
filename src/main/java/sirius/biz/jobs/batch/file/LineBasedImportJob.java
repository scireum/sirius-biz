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
import sirius.biz.jobs.params.VirtualObjectParameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Values;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.web.data.LineBasedProcessor;
import sirius.web.data.RowProcessor;

import java.io.InputStream;

/**
 * Provides a job for importing line based files (CSV, Excel) via a {@link LineBasedImportJobFactory}.
 * <p>
 * Utilizing {@link sirius.biz.importer.ImportHandler import handlers} this can be used as is in most cases. However
 * a subclass overwriting {@link #handleRow(int, Context)} might be required to perform some mappings.
 *
 * @param <E> the type of entities being imported by this job
 */
public class LineBasedImportJob<E extends BaseEntity<?>> extends FileImportJob implements RowProcessor {

    protected final ImportDictionary dictionary;
    protected final EntityDescriptor descriptor;
    protected Class<E> type;
    protected boolean ignoreEmptyValues;

    @Part
    private static Mixing mixing;

    /**
     * Creates a new job for the given factory, name and process.
     *
     * @param fileParameter        the parameter which is used to derive the import file from
     * @param ignoreEmptyParameter the parameter which is used to determine if empty values should be ignored
     * @param type                 the type of entities being imported
     * @param dictionary           the import dictionary to use
     * @param process              the process context itself
     */
    public LineBasedImportJob(VirtualObjectParameter fileParameter,
                              BooleanParameter ignoreEmptyParameter,
                              Class<E> type,
                              ImportDictionary dictionary,
                              ProcessContext process) {
        super(fileParameter, process);
        this.ignoreEmptyValues = process.getParameter(ignoreEmptyParameter).orElse(false);
        this.dictionary = dictionary;
        this.type = type;
        this.descriptor = mixing.getDescriptor(type);
    }

    @Override
    protected void executeForStream(String filename, InputStream in) throws Exception {
        dictionary.resetMappings();
        LineBasedProcessor.create(filename, in).run(this, error -> {
            process.handle(error);
            return true;
        });
    }

    @Override
    protected boolean canHandleFileExtension(String fileExtension) {
        return "xls".equals(fileExtension) || "xlsx".equals(fileExtension) || "csv".equals(fileExtension);
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
            Watch w = Watch.start();
            try {
                Context ctx = filterEmptyValues(dictionary.load(row, false));
                if (!isEmptyContext(ctx)) {
                    handleRow(index, ctx);
                }
            } catch (HandledException e) {
                process.handle(Exceptions.createHandled()
                                         .to(Log.BACKGROUND)
                                         .withNLSKey("LineBasedImportHandler.errorInRow")
                                         .set("row", index)
                                         .set("message", e.getMessage())
                                         .handle());
            } catch (Exception e) {
                process.handle(Exceptions.handle()
                                         .to(Log.BACKGROUND)
                                         .error(e)
                                         .withNLSKey("LineBasedImportHandler.failureInRow")
                                         .set("row", index)
                                         .handle());
            } finally {
                process.addTiming(descriptor.getPluralLabel(), w.elapsedMillis());
            }
        }
    }

    private Context filterEmptyValues(Context source) {
        if (ignoreEmptyValues) {
            return source.removeEmpty();
        }

        return source;
    }

    private boolean isEmptyContext(Context ctx) {
        return ctx.entrySet().stream().noneMatch(entry -> Strings.isFilled(entry.getValue()));
    }

    /**
     * Handles a single row of the import.
     *
     * @param index the index of the row being processed
     * @param ctx   the row represented as context
     */
    protected void handleRow(int index, Context ctx) {
        E entity = findAndLoad(ctx);
        try {
            importer.createOrUpdateInBatch(fillAndVerify(entity));
        } catch (HandledException e) {
            throw Exceptions.createHandled()
                            .withNLSKey("LineBasedImportHandler.cannotHandleEntity")
                            .set("entity", entity.toString())
                            .set("message", e.getMessage())
                            .handle();
        }
    }

    /**
     * Completes the given entity and verifies the integrity of the data.
     *
     * @param entity the entity which has be loaded previously
     * @return the filled and verified entity
     */
    protected E fillAndVerify(E entity) {
        return entity;
    }

    /**
     * Tries to resolve the context into an entity.
     *
     * @param ctx the context containing all relevant data
     * @return the entity which was either found in he database or create using the given data
     */
    protected E findAndLoad(Context ctx) {
        return importer.findAndLoad(type, ctx);
    }
}
