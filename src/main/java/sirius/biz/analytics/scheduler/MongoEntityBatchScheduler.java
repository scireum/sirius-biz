/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.scheduler;

import com.alibaba.fastjson.JSONObject;
import sirius.db.mongo.Mango;
import sirius.db.mongo.MongoEntity;
import sirius.db.mongo.QueryBuilder;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.ValueHolder;
import sirius.kernel.di.std.Part;

import java.util.function.Consumer;

/**
 * Provides a base implementation which schedules all MongoDB entities of a given type using a specified batch size.
 * <p>
 * This is done by creating batches with a specified <tt>startId</tt> and <tt>endId</tt>.
 * <p>
 * Use {@code @Register(classes =  AnalyticsScheduler.class)} to make this scheduler visible to the framework.
 *
 * @param <E> the type of entities being scheduled
 */
public abstract class MongoEntityBatchScheduler<E extends MongoEntity> implements AnalyticsScheduler<E> {

    private static final String START_ID = "startId";
    private static final String END_ID = "endId";

    @Part
    protected Mango mango;

    /**
     * Returns the class of entities being scheduled by this scheduler.
     *
     * @return the type of entities being handled by this scheduler
     */
    protected abstract Class<E> getEntityType();

    /**
     * Determines the size of the batches being generated.
     * <p>
     * Note that the default value is 250 which might be a good guess for most entities. However, this should
     * be verified and measured in production use.
     * </p>
     *
     * @return the batch size being generated
     */
    protected int getBatchSize() {
        return 250;
    }

    @Override
    public void scheduleBatches(Consumer<JSONObject> batchConsumer) {
        TaskContext taskContext = TaskContext.get();
        ValueHolder<String> lastLimit = ValueHolder.of("");
        while (taskContext.isActive()) {
            ValueHolder<String> nextLimit = ValueHolder.of(lastLimit.get());
            mango.select(getEntityType())
                 .fields(MongoEntity.ID)
                 .where(QueryBuilder.FILTERS.gt(MongoEntity.ID, lastLimit.get()))
                 .orderAsc(MongoEntity.ID)
                 .limit(getBatchSize())
                 .iterateAll(e -> {
                     nextLimit.set(e.getId());
                 });

            JSONObject batch = new JSONObject();
            batch.put(START_ID, lastLimit.get());
            batch.put(END_ID, nextLimit.get());
            batchConsumer.accept(batch);

            if (lastLimit.get().equals(nextLimit.get())) {
                return;
            }

            lastLimit.set(nextLimit.get());
        }
    }

    @Override
    public void collectBatch(JSONObject batchDescription, Consumer<E> entityConsumer) {
        long startId = batchDescription.getLongValue(START_ID);
        long endId = batchDescription.getLongValue(END_ID);

        mango.select(getEntityType())
             .where(QueryBuilder.FILTERS.gte(MongoEntity.ID, startId))
             .where(QueryBuilder.FILTERS.lte(MongoEntity.ID, endId))
             .iterateAll(entityConsumer);
    }
}
