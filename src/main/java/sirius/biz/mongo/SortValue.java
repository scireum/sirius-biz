/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.mongo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks all properties to be included in {@link SortField#SORT_FIELD} to build a normalized sort field for MongoDB.
 * <p>
 * Note that if a <tt>SortValue</tt> cannot be used (e.g. in a shared composite), {@link CustomSortValues} can be
 * used instead.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SortValue {

    /**
     * Determines to order of this field within all fields wearing a {@link SortValue}.
     *
     * @return the sort order / priority
     */
    int order();
}
