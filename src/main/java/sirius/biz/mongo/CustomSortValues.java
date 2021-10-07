/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.mongo;

import sirius.db.mongo.MongoEntity;

import java.util.function.Consumer;

/**
 * Can be implemented by a {@link MongoEntity} to supply custom sort values in cases where {@link SortValue} cannot
 * be used.
 *
 * @see SortValue
 */
public interface CustomSortValues {

    /**
     * Emits all values to sort by.
     *
     * @param sortValueConsumer a consumer to be supplied with all values to sort by
     */
    void emitSortValues(Consumer<Object> sortValueConsumer);
}
