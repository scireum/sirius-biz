/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.db.mixing.Entity;
import sirius.kernel.di.std.AutoRegister;
import sirius.kernel.di.std.Named;
import sirius.web.controller.AutocompleteHelper;
import sirius.web.http.WebContext;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * Allows to supply completions for entity-based token-autocomplete fields.
 *
 * @param <T> the type of the entity to autocomplete
 * @implNote the Autocompleter could be more generic to allow non-entity-autocompletes, but for now, this is not needed.
 */
@AutoRegister
public interface Autocompleter<T extends Entity> extends Named {
    /**
     * Computes the possible completions for the query and the web context.
     * <p>
     * Implementations may return the items in an ordered collection. This order is retained.
     *
     * @param query      the query to search for
     * @param webContext the web context with possibly additional context to the search
     * @return a potentially ordered collection of completions
     */
    Collection<T> getCompletions(String query, WebContext webContext);

    /**
     * A simple method to convert an entity to a label that can be displayed.
     *
     * @param entity the entity
     * @return a displayable string
     */
    default String toLabel(T entity) {
        return entity.toString();
    }

    /**
     * Determines whether the resulting autocomplete entities should be accessible for not logged-in users.
     *
     * @return <tt>true</tt> if no login is required, <tt>false</tt> if only logged-in users must access
     */
    default boolean publicAccessible() {
        return false;
    }

    /**
     * Wrap the completions from {@link #getCompletions} to autocompletions using {@link #toLabel}.
     *
     * @param query              the query to search for
     * @param webContext         the context to provide additional search context
     * @param completionConsumer a consumer that handles the completions
     */
    default void suggest(String query,
                         WebContext webContext,
                         Consumer<AutocompleteHelper.Completion> completionConsumer) {
        getCompletions(query, webContext).stream()
                                         .map(entity -> AutocompleteHelper.suggest(entity.getIdAsString())
                                                                          .withCompletionLabel(toLabel(entity))
                                                                          .withFieldLabel(toLabel(entity)))
                                         .forEach(completionConsumer);
    }
}
