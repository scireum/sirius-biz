/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.db.mixing.Entity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Provides suggestions for {@link QueryTag}s for a given search term.
 */
public interface QueryTagSuggester {

    /**
     * Computes suggestions for the given parameters.
     *
     * @param type       the type of entities being filtered.
     * @param entityType the type resolved as {@link Class} if possible
     * @param searchTerm the query or prefix supplied by the user
     * @param consumer   the consumer to send suggestions to
     */
    void computeQueryTags(@Nonnull String type,
                          @Nullable Class<? extends Entity> entityType,
                          @Nonnull String searchTerm,
                          @Nonnull Consumer<QueryTag> consumer);

}
