/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch;

import sirius.biz.importer.Importer;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.kernel.health.Exceptions;

import java.io.IOException;
import java.util.Objects;

/**
 * Provides a base class for batch jobs which utilize an {@link Importer} to import data.
 */
public abstract class ImportJob extends BatchJob {

    /**
     * Contains the importer which can be used to import data
     */
    protected final Importer importer;

    /**
     * Creates a new job for the given process context.
     *
     * @param process the process context in which the job is executed
     */
    protected ImportJob(ProcessContext process) {
        super(process);
        this.importer = new Importer(process.getTitle());
    }

    /**
     * Enusures or establishes a parent child relation.
     * <p>
     * For new entities (owner), the given reference is initialized with the given entity. For existing entities
     * it is verified, that the given reference points to the given entity.
     *
     * @param owner  the entity which contains the reference
     * @param ref    the reference which is either filled or verified that it points to <tt>entity</tt>
     * @param entity the entity the reference must point to
     * @param <E>    the generic type the the entity being referenced
     * @throws sirius.kernel.health.HandledException if the entities do no match
     */
    protected <I, E extends BaseEntity<I>> void setOrVerify(BaseEntity<?> owner, BaseEntityRef<I, E> ref, E entity) {
        if (!Objects.equals(ref.getId(), entity.getId())) {
            if (owner.isNew()) {
                ref.setValue(entity);
            } else {
                throw Exceptions.createHandled().withNLSKey("ImportJob.invalidReference").handle();
            }
        }
    }

    @Override
    public void close() throws IOException {
        try {
            if (importer.getContext().hasBatchContext()) {
                process.log(ProcessLog.info()
                                      .withMessage(importer.getContext().getBatchContext().toString())
                                      .asSystemMessage());
            }
            this.importer.close();
        } catch (IOException e) {
            process.handle(e);
        }
        super.close();
    }
}
