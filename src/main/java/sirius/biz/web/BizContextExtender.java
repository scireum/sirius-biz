/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.biz.codelists.CodeLists;
import sirius.biz.jobs.Jobs;
import sirius.db.es.Elastic;
import sirius.db.jdbc.Databases;
import sirius.db.jdbc.OMA;
import sirius.db.mongo.Mango;
import sirius.db.mongo.Mongo;
import sirius.db.redis.Redis;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.templates.GlobalContextExtender;

import java.util.function.BiConsumer;

/**
 * Makes central frameworks available in Tagliatelle without any import or reference.
 */
@Register
public class BizContextExtender implements GlobalContextExtender {

    @Part
    private CodeLists<?, ?, ?> codeLists;

    @Part
    private Databases databases;

    @Part
    private Redis redis;

    @Part
    private OMA oma;

    @Part
    private Mongo mongo;

    @Part
    private Mango mango;

    @Part
    private Elastic elastic;

    @Part
    private Jobs jobs;

    @Override
    public void collectTemplate(BiConsumer<String, Object> globalParameterCollector) {
        globalParameterCollector.accept("codeLists", codeLists);
        globalParameterCollector.accept("jobsService", jobs);
    }

    @Override
    public void collectScripting(BiConsumer<String, Object> globalParameterCollector) {
        globalParameterCollector.accept("oma", oma);
        globalParameterCollector.accept("mongo", mongo);
        globalParameterCollector.accept("mango", mango);
        globalParameterCollector.accept("elastic", elastic);
        globalParameterCollector.accept("databases", databases);
        globalParameterCollector.accept("redis", redis);
    }
}
