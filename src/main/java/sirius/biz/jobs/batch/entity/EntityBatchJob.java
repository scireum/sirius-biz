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
import sirius.db.mixing.BaseMapper;
import sirius.db.mixing.Mixing;
import sirius.db.mixing.query.Query;
import sirius.kernel.di.std.Part;

/**
 * Base class for iterating over {@link BaseEntity entities} in the database
 * to process them (e.g. for exporting).
 *
 * @param <E> the {@link BaseEntity entity} to iterate
 */
public abstract class EntityBatchJob<E extends BaseEntity<?>> extends BatchJob {

    private Class<E> type;
    private BaseMapper<E, ?, ?> entityMapper;

    @Part
    protected static Mixing mixing;

    /**
     * Creates a new job for the given process context and entity class.
     *
     * @param process the process context in which the job is executed
     * @param type    the class of the entity
     */
    public EntityBatchJob(ProcessContext process, Class<E> type) {
        super(process);
        this.type = type;
    }

    @Override
    public void execute() throws Exception {
        Query<?, E, ?> entityQuery = selectEntityQuery();
        addQueryFilters(entityQuery);
        entityQuery.iterateAll(this::processEntity);
    }

    @SuppressWarnings("unchecked")
    private <Q extends Query<Q, E, ?>> Q selectEntityQuery() {
        if (entityMapper == null) {
            entityMapper = mixing.getDescriptor(type).getMapper();
        }
        return (Q) entityMapper.select(type);
    }

    /**
     * Adds filters to the query to select the {@link BaseEntity entity}.
     *
     * @param query the query to add filters to
     * @param <Q>   the query properties
     */
    protected abstract <Q extends Query<?, E, ?>> void addQueryFilters(Q query);

    /**
     * Processes a selected {@link BaseEntity entity}.
     *
     * @param entity the entity to process
     */
    protected abstract void processEntity(E entity);
}
