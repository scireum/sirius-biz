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
 * Created by aha on 27.01.17.
 */
public interface QueryTagSuggester {

    void computeQueryTags(@Nonnull String type,
                          @Nullable Class<? extends Entity> entityType,
                          @Nonnull String searchTerm,
                          @Nonnull Consumer<QueryTag> consumer);

}
