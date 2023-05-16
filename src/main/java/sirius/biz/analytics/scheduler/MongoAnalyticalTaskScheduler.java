/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.scheduler;

import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.db.mongo.MongoEntity;
import sirius.db.mongo.MongoQuery;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Json;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Provides a base implementation which schedules all MongoDB entities matching a given set of tasks (defined by a
 * subclass of {@link AnalyticalTask}) using a specified batch size (utilizing {@link MongoEntityBatchEmitter}).
 * <p>
 * Use {@code @Register(classes = AnalyticsScheduler.class)} to make this scheduler visible to the framework.
 */
public abstract class MongoAnalyticalTaskScheduler extends BaseAnalyticalTaskScheduler<MongoEntity> {

    @Part
    private MongoEntityBatchEmitter batchEmitter;

    @Override
    protected Class<?> getMinimalTargetType() {
        return MongoEntity.class;
    }

    @Override
    protected void scheduleBatches(Class<? extends MongoEntity> type, Consumer<ObjectNode> batchConsumer) {
        try {
            if (Modifier.isAbstract(type.getModifiers())) {
                // We use MongoEntity.class as place-holder to run global metric computers.
                // In this case we execute a simple run for "null"...
                batchConsumer.accept(Json.createObject()
                                         .put(BaseAnalyticalTaskScheduler.CONTEXT_MARKER_GLOBAL_ENTITY, true));
            } else if (mixing.findDescriptor(type).isPresent()) {
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
    protected <E extends MongoEntity> void extendBatchQuery(MongoQuery<E> query) {
        // empty by default
    }

    @Override
    protected boolean isMatchingEntityType(AnalyticalTask<?> task) {
        return MongoEntity.class.isAssignableFrom(task.getType());
    }

    @Override
    public void executeBatch(ObjectNode batchDescription, LocalDate date, int level) {
        if (batchDescription.has(BaseAnalyticalTaskScheduler.CONTEXT_MARKER_GLOBAL_ENTITY)) {
            AnalyticalEngine.LOG.FINE("Executing global batch: " + batchDescription);

            // A global run was detected, execute with "null" and MongoEntity as type...
            executeEntity(null, MongoEntity.class, date, level);
        } else if (batchDescription.has(BaseEntityBatchEmitter.TYPE)) {
            // Note that we check for the presence of the TYPE, as (in case of some schedulers) we emit
            // an empty batch, just to ensure that executors with the higher levels are executed, even if
            // there are no tasks on the current level (see AnalyticalBatchExecutor.executeWork...)
            AtomicInteger counter = new AtomicInteger();
            batchEmitter.evaluateBatch(batchDescription, this::extendBatchQuery, e -> {
                executeEntity(e, e.getClass(), date, level);
                counter.incrementAndGet();
            });

            AnalyticalEngine.LOG.FINE("Executed %s entities for batch: %s", counter.get(), batchDescription);
        } else {
            AnalyticalEngine.LOG.FINE("Skipped a completely empty batch: %s", batchDescription);
        }
    }
}
