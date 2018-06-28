/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.db.mongo.Mongo;
import sirius.db.mongo.MongoEntity;
import sirius.db.mongo.MongoQuery;
import sirius.db.mongo.constraints.MongoConstraint;
import sirius.kernel.di.std.Part;

/**
 * Implements a page helper for {@link MongoQuery MongoDB queries}.
 *
 * @param <E> the generic type of the entities being queried
 */
public class MongoPageHelper<E extends MongoEntity>
        extends BasePageHelper<E, MongoConstraint, MongoQuery<E>, MongoPageHelper<E>> {

    @Part
    private Mongo mongo;

    protected MongoPageHelper(MongoQuery<E> query) {
        super(query);
    }

    /**
     * Creates a new instance with the given base query.
     *
     * @param baseQuery the initial query to execute
     * @param <E>       the generic entity type being queried
     * @return a new instance operating on the given base query
     */
    public static <E extends MongoEntity> MongoPageHelper<E> withQuery(MongoQuery<E> baseQuery) {
        return new MongoPageHelper<>(baseQuery);
    }
}
