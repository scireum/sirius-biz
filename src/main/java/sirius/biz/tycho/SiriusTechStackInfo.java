/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho;

import sirius.biz.analytics.events.EventRecorder;
import sirius.biz.jupiter.Jupiter;
import sirius.db.es.Elastic;
import sirius.db.jdbc.schema.Schema;
import sirius.db.mixing.Mixing;
import sirius.db.mongo.Mongo;
import sirius.db.redis.Redis;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.di.std.Register;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;

/**
 * Provides/renders the tech stack commonly used by sirius.
 */
@Register
public class SiriusTechStackInfo implements TechStackInfo {

    @Part
    @Nullable
    private Jupiter jupiter;

    @Part
    @Nullable
    private Redis redis;

    @Part
    @Nullable
    private Mongo mongo;

    @Part
    @Nullable
    private Elastic elastic;

    @Part
    @Nullable
    private EventRecorder eventRecorder;

    @Part
    @Nullable
    private Schema schema;

    @Override
    public void fetchActiveTechnologies(BiConsumer<String, String> collector) {
        collector.accept("/assets/images/techstack/sirius.png", "https://www.sirius-lib.net");

        if (jupiter != null && jupiter.getDefault().isConfigured()) {
            collector.accept("/assets/images/techstack/jupiter.png", "https://github.com/scireum/jupiter");
        }

        collector.accept("/assets/images/techstack/netty.png", "https://netty.io");

        if (mongo != null && mongo.isConfigured()) {
            collector.accept("/assets/images/techstack/mongodb.png", "https://www.mongodb.com/");
        }
        if (schema != null && schema.isConfigured(Mixing.DEFAULT_REALM)) {
            collector.accept("/assets/images/techstack/mariadb.png", "https://mariadb.com");
        }
        if (redis != null && redis.isConfigured()) {
            collector.accept("/assets/images/techstack/redis.png", "https://redis.io");
        }
        if (eventRecorder != null && eventRecorder.isConfigured()) {
            collector.accept("/assets/images/techstack/clickhouse.png", "https://clickhouse.com");
        }
        if (elastic != null && elastic.isConfigured()) {
            collector.accept("/assets/images/techstack/elastic.png", "https://www.elastic.co/");
        }
    }

    @Override
    public int getPriority() {
        return Priorized.DEFAULT_PRIORITY;
    }
}
