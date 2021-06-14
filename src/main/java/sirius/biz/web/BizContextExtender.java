/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.biz.codelists.CodeLists;
import sirius.biz.isenguard.Isenguard;
import sirius.biz.jobs.Jobs;
import sirius.biz.tenants.Tenants;
import sirius.db.es.Elastic;
import sirius.db.jdbc.Databases;
import sirius.db.jdbc.OMA;
import sirius.db.mongo.Mango;
import sirius.db.mongo.Mongo;
import sirius.db.redis.Redis;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.templates.GlobalContextExtender;

import javax.annotation.Nullable;

/**
 * Makes central frameworks available in Tagliatelle without any import or reference.
 */
@Register
public class BizContextExtender implements GlobalContextExtender {

    @Part
    @Nullable
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

    @Part
    private Isenguard isenguard;

    @Part
    @Nullable
    private Tenants<?, ?, ?> tenantsHelper;

    @Override
    public void collectTemplate(Collector globalParameterCollector) {
        globalParameterCollector.collect("codeLists", codeLists, CodeLists.class);
        globalParameterCollector.collect("jobsService", jobs, Jobs.class);
        globalParameterCollector.collect("isenguard", isenguard, Isenguard.class);
        globalParameterCollector.collect("tenantsHelper", tenantsHelper, Tenants.class);
        globalParameterCollector.collect("appBaseUrl", BizController.getBaseUrl(), String.class);
    }

    @Override
    public void collectScripting(Collector globalParameterCollector) {
        globalParameterCollector.collect("oma", oma, OMA.class);
        globalParameterCollector.collect("mongo", mongo, Mongo.class);
        globalParameterCollector.collect("mango", mango, Mango.class);
        globalParameterCollector.collect("elastic", elastic, Elastic.class);
        globalParameterCollector.collect("databases", databases, Databases.class);
        globalParameterCollector.collect("redis", redis, Redis.class);
    }
}
