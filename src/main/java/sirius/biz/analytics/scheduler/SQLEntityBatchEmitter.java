/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.scheduler;

import com.alibaba.fastjson.JSONObject;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SQLEntity;
import sirius.db.jdbc.SmartQuery;
import sirius.db.mixing.Mixing;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.ValueHolder;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Provides a facility to execute a query into a set of batch descriptions.
 * <p>
 * These batches are described using JSON and can be evaluated into an iterator of entities using
 * {@link #evaluateBatch(JSONObject, Consumer, Consumer)}.
 */
@Register(classes = SQLEntityBatchEmitter.class)
public class SQLEntityBatchEmitter {

    private static final String TYPE = "type";
    private static final String START_ID = "startId";
    private static final String END_ID = "endId";

    @Part
    protected OMA oma;

    @Part
    protected Mixing mixing;

    /**
     * Creates a query for the given type of entities and yields a number of batch descriptions.
     *
     * @param type          the type of entities being queried
     * @param queryExtender an extender which can further narrow down the entities being queried
     * @param batchSize     the size of each batch
     * @param batchConsumer a consumer which processes the batch descriptions
     * @param <E>           the type of entities being processed
     */
    public <E extends SQLEntity> void computeBatches(Class<E> type,
                                                     @Nullable Consumer<SmartQuery<E>> queryExtender,
                                                     int batchSize,
                                                     Consumer<JSONObject> batchConsumer) {
        TaskContext taskContext = TaskContext.get();
        ValueHolder<Long> lastLimit = ValueHolder.of(0L);
        while (taskContext.isActive()) {
            ValueHolder<Long> nextLimit = ValueHolder.of(lastLimit.get());
            SmartQuery<E> query = oma.selectFromSecondary(type)
                                     .fields(SQLEntity.ID)
                                     .where(OMA.FILTERS.gt(SQLEntity.ID, lastLimit.get()));
            if (queryExtender != null) {
                queryExtender.accept(query);
            }

            query.orderAsc(SQLEntity.ID).limit(batchSize).iterateAll(e -> {
                nextLimit.set(e.getId());
            });

            JSONObject batch = new JSONObject();
            batch.put(TYPE, Mixing.getNameForType(type));
            batch.put(START_ID, lastLimit.get());
            batch.put(END_ID, nextLimit.get());
            batchConsumer.accept(batch);

            if (lastLimit.get().equals(nextLimit.get())) {
                return;
            }

            lastLimit.set(nextLimit.get());
        }
    }

    /**
     * Resolves a JSON batch description and supplies the given consumer with all associated entities.
     *
     * @param batchDescription the batch description as generated by
     *                         {@link #computeBatches(Class, Consumer, int, Consumer)}
     * @param queryExtender    the query extender which was also passed into <tt>computeBatches</tt>
     * @param entityConsumer   the consumer to be supplid with all entities in the batch
     * @param <E>              the type of entities being resolved
     */
    @SuppressWarnings("unchecked")
    public <E extends SQLEntity> void evaluateBatch(JSONObject batchDescription,
                                                    @Nullable Consumer<SmartQuery<E>> queryExtender,
                                                    Consumer<E> entityConsumer) {
        long startId = batchDescription.getLongValue(START_ID);
        long endId = batchDescription.getLongValue(END_ID);
        String typeName = batchDescription.getString(TYPE);

        Class<E> type = (Class<E>) mixing.getDescriptor(typeName).getType();
        SmartQuery<E> query = oma.selectFromSecondary(type)
                                 .where(OMA.FILTERS.gte(SQLEntity.ID, startId))
                                 .where(OMA.FILTERS.lte(SQLEntity.ID, endId));
        if (queryExtender != null) {
            queryExtender.accept(query);
        }

        query.iterateAll(entityConsumer);
    }
}
