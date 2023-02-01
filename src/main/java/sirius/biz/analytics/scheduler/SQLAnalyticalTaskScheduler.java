/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.scheduler;

import com.alibaba.fastjson.JSONObject;
import sirius.db.jdbc.SQLEntity;
import sirius.db.jdbc.SmartQuery;
import sirius.kernel.commons.Explain;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * Provides a base implementation which schedules all JDBC entities matching a given set of tasks (defined by a
 * subclass of {@link AnalyticalTask}) using a specified batch size (utilizing {@link SQLEntityBatchEmitter}).
 * <p>
 * Use {@code @Register(classes = AnalyticsScheduler.class)} to make this scheduler visible to the framework.
 */
public abstract class SQLAnalyticalTaskScheduler extends BaseAnalyticalTaskScheduler<SQLEntity> {

    @Part
    private SQLEntityBatchEmitter batchEmitter;

    @Override
    protected Class<?> getMinimalTargetType() {
        return SQLEntity.class;
    }

    @Override
    protected void scheduleBatches(Class<? extends SQLEntity> type, Consumer<JSONObject> batchConsumer) {
        try {
            if (Modifier.isAbstract(type.getModifiers()) || mixing.findDescriptor(type).isEmpty()) {
                batchConsumer.accept(new JSONObject());
            } else {
                batchEmitter.computeBatches(type, this::extendBatchQuery, getBatchSize(), batch -> {
                    batchConsumer.accept(batch);
                    return true;
                });
            }
        } catch (Exception e) {
            Exceptions.handle(Log.BACKGROUND, e);
        }
    }

    /**
     * Defines the number of entities being processed per batch.
     *
     * @return the size of a single batch
     */
    @SuppressWarnings("squid:S3400")
    @Explain("Subclasses can overwrite this method to change the batch size")
    protected int getBatchSize() {
        return 250;
    }

    /**
     * Can be used to further narrow down the entities being processed by this scheduler.
     *
     * @param query the query to extend
     * @param <E>   the type of entities being processed
     */
    protected <E extends SQLEntity> void extendBatchQuery(SmartQuery<E> query) {
        // empty by default
    }

    @Override
    protected boolean isMatchingEntityType(AnalyticalTask<?> task) {
        return SQLEntity.class.isAssignableFrom(task.getType());
    }

    @Override
    public void executeBatch(JSONObject batchDescription, LocalDate date, int level) {
        if (!batchDescription.containsKey(BaseEntityBatchEmitter.TYPE)) {
            executeEntity(null, SQLEntity.class, date, level);
        } else {
            batchEmitter.evaluateBatch(batchDescription, this::extendBatchQuery, e -> executeEntity(e,e.getClass(), date, level));
        }
    }
}
