/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.scheduler;

import com.alibaba.fastjson.JSONObject;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SQLEntity;
import sirius.db.jdbc.SmartQuery;
import sirius.db.jdbc.constraints.SQLConstraint;
import sirius.db.mixing.BaseMapper;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import java.util.function.Consumer;

/**
 * Provides a facility to execute a query into a set of batch descriptions.
 * <p>
 * These batches are described using JSON and can be evaluated into an iterator of entities using
 * {@link #evaluateBatch(JSONObject, Consumer, Consumer)}.
 */
@Register(classes = SQLEntityBatchEmitter.class)
public class SQLEntityBatchEmitter
        extends BaseEntityBatchEmitter<Long, SQLConstraint, SQLEntity, SmartQuery<SQLEntity>> {

    @Part
    private OMA oma;

    @Override
    protected BaseMapper<SQLEntity, SQLConstraint, ?> getMapper() {
        return oma;
    }
}
