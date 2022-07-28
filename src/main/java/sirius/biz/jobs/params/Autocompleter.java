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
 * An abstract class that can be implemented to supply {@link AutocompleteHelper.Completion Completions} for entity-based token-autocomplete fields.
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
     * @param query   the query to search for
     * @param context the web context with possibly additional context to the search
     * @return a potentially ordered collection of completions
     */
    Collection<T> getCompletions(String query, WebContext context);

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
     * Wrap the completions from {@link #getCompletions} to autocompletions using {@link #toLabel}.
     *
     * @param query              the query to search for
     * @param context            the context to provide additional search context
     * @param completionConsumer a consumer that handles the completions
     */
    default void suggest(String query, WebContext context, Consumer<AutocompleteHelper.Completion> completionConsumer) {
        getCompletions(query, context).stream()
                                      .map(entity -> AutocompleteHelper.suggest(entity.getIdAsString())
                                                                       .withCompletionLabel(toLabel(entity))
                                                                       .withFieldLabel(toLabel(entity)))
                                      .forEach(completionConsumer);
    }
}
