/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.biz.codelists.CodeLists;
import sirius.db.jdbc.Databases;
import sirius.db.mixing.OMA;
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
    private CodeLists codeLists;

    @Part
    private Databases databases;

    @Part
    private Redis redis;

    @Part
    private OMA oma;

    @Override
    public void collectTemplate(BiConsumer<String, Object> globalParameterCollector) {
        globalParameterCollector.accept("codeLists", codeLists);
    }

    @Override
    public void collectScripting(BiConsumer<String, Object> globalParameterCollector) {
        globalParameterCollector.accept("oma", oma);
        globalParameterCollector.accept("databases", databases);
        globalParameterCollector.accept("redis", redis);
    }
}
