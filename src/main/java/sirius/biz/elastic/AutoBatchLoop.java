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

@Register(classes = {AutoBatchLoop.class, BackgroundLoop.class})
public class AutoBatchLoop extends BackgroundLoop {

    private static final int MAX_ENTITIES_PER_RUN = 5000;
    private static final int MAX_QUEUED_ENTITIES = 20000;
    private LocalDateTime frozenUntil;
    private ConcurrentLinkedDeque<ElasticEntity> entities = new ConcurrentLinkedDeque<>();
    private AtomicInteger queuedEntities = new AtomicInteger();

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

    public void insertAsync(ElasticEntity entity) {
        if (entity != null && queuedEntities.get() < MAX_QUEUED_ENTITIES) {
            entities.add(entity);
            queuedEntities.incrementAndGet();
        }
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
