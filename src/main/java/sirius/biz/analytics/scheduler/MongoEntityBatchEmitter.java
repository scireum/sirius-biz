/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.scheduler;

import com.alibaba.fastjson.JSONObject;
import sirius.db.mixing.BaseMapper;
import sirius.db.mongo.Mango;
import sirius.db.mongo.MongoEntity;
import sirius.db.mongo.MongoQuery;
import sirius.db.mongo.constraints.MongoConstraint;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import java.util.function.Consumer;

/**
 * Provides a facility to execute a query into a set of batch descriptions.
 * <p>
 * These batches are described using JSON and can be evaluated into an iterator of entities using
 * {@link #evaluateBatch(JSONObject, Consumer, Consumer)}.
 */
@Register(classes = MongoEntityBatchEmitter.class)
public class MongoEntityBatchEmitter extends BaseEntityBatchEmitter<String, MongoConstraint, MongoEntity, MongoQuery<MongoEntity>> {

    @Part
    private Mango mango;


    @Override
    protected BaseMapper<MongoEntity, MongoConstraint, ?> getMapper() {
        return mango;
    }
}
