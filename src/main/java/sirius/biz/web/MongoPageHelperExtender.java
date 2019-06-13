/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.db.mongo.MongoEntity;
import sirius.kernel.di.std.Priorized;

/**
 * Permits to extend a {@link BasePageHelper page helper} for an entity class.
 *
 * @param <E> the type of entities being contained in the page.
 */
public interface MongoPageHelperExtender<E extends MongoEntity> extends Priorized {

    /**
     * Extends the given page helper.
     * <p>
     * This might be used to add a term filter for a field in a {@link sirius.db.mixing.annotations.Mixin mixin}.
     *
     * @param pageHelper the page helper to extend.
     */
    void extend(MongoPageHelper<E> pageHelper);

    /**
     * Returns the target entity type addresed by this extender.
     *
     * @return the target entity for which the page helper is to be extended
     */
    Class<E> getTargetType();
}
