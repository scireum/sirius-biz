/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.entity;

import sirius.biz.jobs.batch.BatchJob;
import sirius.biz.process.ProcessContext;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;
import sirius.db.mixing.query.Query;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;

/**
 * Base class for iterating over {@link BaseEntity entities} in the database
 * to process them (e.g. for exporting).
 *
 * @param <E> the {@link BaseEntity entity} to iterate
 * @param <Q> the {@link Query} to use for selects
 */
public abstract class EntityBatchJob<E extends BaseEntity<?>, Q extends Query<Q, E, ?>> extends BatchJob {

    protected final EntityDescriptor descriptor;
    protected Class<E> type;

    @Part
    private static Mixing mixing;

    /**
     * Creates a new job for the given process context and entity class.
     *
     * @param process the process context in which the job is executed
     * @param type    the type of entities being processed
     */
    protected EntityBatchJob(ProcessContext process, Class<E> type) {
        super(process);
        this.type = type;
        this.descriptor = mixing.getDescriptor(type);
    }

    @Override
    public void execute() throws Exception {
        createQuery().streamBlockwise().forEach(this::handleEntity);
    }

    private void handleEntity(E entity) {
        Watch w = Watch.start();
        try {
            processEntity(entity);
        } catch (Exception exception) {
            process.handle(exception);
        } finally {
            process.addTiming(descriptor.getPluralLabel(), w.elapsedMillis());
        }
    }

    /**
     * Creates the query to select the entity which will be iterated over.
     * <p>
     * It it strongly advised to add an order by to ensure a consistent order
     * of the entities.
     *
     * @return the query to iterate over
     */
    protected abstract Q createQuery();

    /**
     * Processes a selected {@link BaseEntity entity}.
     *
     * @param entity the entity to process
     */
    protected abstract void processEntity(E entity);
}
