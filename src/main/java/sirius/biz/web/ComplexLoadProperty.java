/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.db.mixing.BaseEntity;
import sirius.web.http.WebContext;

/**
 * Marks {@link sirius.db.mixing.Property properties} which are aware of {@link WebContext web contexts} and handle
 * their save logic themselves.
 * <p>
 * This is used by <tt>BizController#tryLoadProperty(WebContext, BaseEntity, Property)</tt> to delegate loading
 * to the property itself.
 */
public interface ComplexLoadProperty {

    /**
     * Loads data from the given web context.
     *
     * @param webContext the request to read data from
     * @param entity     the entity to fill
     * @return <tt>true</tt> if loading succeeded, <tt>false</tt> if an error occurred
     */
    boolean loadFromWebContext(WebContext webContext, BaseEntity<?> entity);
}
