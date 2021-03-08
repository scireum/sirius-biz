/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.elastic;

import sirius.db.es.BulkContext;
import sirius.db.es.Elastic;
import sirius.db.es.ElasticEntity;
import sirius.kernel.async.BackgroundLoop;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Collects and bulk-inserts {@link ElasticEntity entities} to be inserted into Elasticsearch.
 * <p>
 * Note that when worse comes to worst, this framework rather drops entities to insert than to crash the system.
 * Therefore this should only be used for non-critical tasks (e.g. log entries).
 */
@Register(classes = {AutoBatchLoop.class, BackgroundLoop.class})
public class AutoBatchLoop extends BackgroundLoop {

    private static final int MAX_ENTITIES_PER_RUN = 5000;
    private static final int MAX_QUEUED_ENTITIES = 20000;
    private LocalDateTime frozenUntil;
    private final ConcurrentLinkedDeque<ElasticEntity> entities = new ConcurrentLinkedDeque<>();
    private final AtomicInteger queuedEntities = new AtomicInteger();

    @Part
    private Elastic elastic;

    @Nonnull
    @Override
    public String getName() {
        return "elastic-auto-batch";
    }

    @Override
    public double maxCallFrequency() {
        return 0.5d;
    }

    /**
     * Collects and bulk-inserts the entity in a separate thread.
     * <p>
     * Note that in a heavily overloaded system, the entity might be dropped in favor of not crashing the system.
     * Therefore this must not be used for critical data or the return value of this call has to be observed
     * carefully.
     *
     * @param entity the entity to bulk-insert into Elasticsearch
     * @return <tt>true</tt> if the entity was successfully queued, <tt>false</tt> otherwise
     */
    public boolean insertAsync(ElasticEntity entity) {
        if (entity == null) {
            return true;
        }
        if (queuedEntities.get() < MAX_QUEUED_ENTITIES) {
            entities.add(entity);
            queuedEntities.incrementAndGet();
            return true;
        }

        return false;
    }

    @Nullable
    @Override
    protected String doWork() throws Exception {
        if (entities.isEmpty()) {
            return null;
        }

        if (frozenUntil != null) {
            if (LocalDateTime.now().isAfter(frozenUntil)) {
                frozenUntil = null;
            } else {
                return Strings.apply("Frozen until: %s", NLS.toUserString(frozenUntil));
            }
        }

        int entitiesProcessed = executeBatchInsert();

        return Strings.apply("Inserted %s entities...", entitiesProcessed);
    }

    private int executeBatchInsert() {
        int entitiesProcessed = 0;
        try (BulkContext bulkContext = elastic.batch()) {
            ElasticEntity entity = entities.poll();
            while (entity != null) {
                queuedEntities.decrementAndGet();
                bulkContext.overwrite(entity);

                if (entitiesProcessed++ >= MAX_ENTITIES_PER_RUN) {
                    return entitiesProcessed;
                }
                entity = entities.poll();
            }
        } catch (Exception e) {
            Exceptions.handle(Log.BACKGROUND, e);
            frozenUntil = LocalDateTime.now().plusSeconds(10);
        }

        return entitiesProcessed;
    }
}
