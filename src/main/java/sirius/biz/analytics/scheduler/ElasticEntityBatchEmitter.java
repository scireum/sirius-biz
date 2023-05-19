/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.scheduler;

import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.db.es.Elastic;
import sirius.db.es.ElasticEntity;
import sirius.db.es.ElasticQuery;
import sirius.db.es.constraints.ElasticConstraint;
import sirius.db.mixing.BaseMapper;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import java.util.function.Consumer;

/**
 * Provides a facility to execute a query into a set of batch descriptions.
 * <p>
 * These batches are described using JSON and can be evaluated into an iterator of entities using
 * {@link #evaluateBatch(ObjectNode, Consumer, Consumer)}.
 */
@Register(classes = ElasticEntityBatchEmitter.class)
public class ElasticEntityBatchEmitter
        extends BaseEntityBatchEmitter<String, ElasticConstraint, ElasticEntity, ElasticQuery<ElasticEntity>> {

    @Part
    protected Elastic elastic;

    @Override
    protected BaseMapper<ElasticEntity, ElasticConstraint, ?> getMapper() {
        return elastic;
    }
}
