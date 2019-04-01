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
import sirius.db.mixing.query.Query;

/**
 * Base class for iterating over {@link BaseEntity entities} in the database
 * to process them (e.g. for exporting).
 *
 * @param <E> the {@link BaseEntity entity} to iterate
 */
public abstract class EntityBatchJob<E extends BaseEntity<?>> extends BatchJob {

    /**
     * Creates a new job for the given process context and entity class.
     *
     * @param process the process context in which the job is executed
     */
    public EntityBatchJob(ProcessContext process) {
        super(process);
    }

    @Override
    public void execute() throws Exception {
        createQuery().iterateAll(this::handleEntity);
    }

    private void handleEntity(E entity) {
        if (process.isActive()) {
            try {
                processEntity(entity);
            } catch (Exception e) {
                process.handle(e);
            }
        }
    }

    /**
     * Creates the query to select the entity which will be iterated over.
     * <p>
     * It it strongly advised to add an order by to ensure a consistent order
     * of the entities.
     *
     * @param <Q> the query properties
     * @return the query to iterate over
     */
    protected abstract <Q extends Query<?, E, ?>> Q createQuery();

    /**
     * Processes a selected {@link BaseEntity entity}.
     *
     * @param entity the entity to process
     */
    protected abstract void processEntity(E entity);
}
