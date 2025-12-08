/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.search;

import sirius.kernel.di.std.AutoRegister;
import sirius.kernel.di.std.Priorized;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Represents a provider which participates in the system wide search of the Tycho UI.
 * <p>
 * A provider needs to wear a {@link sirius.kernel.di.std.Register} annotation in order to be discovered by the
 * framework.
 */
@AutoRegister
public interface OpenSearchProvider extends Priorized {

    /**
     * Contains the label to show for the card generated for results yielded by this provider.
     *
     * @return the label of the category of results as yielded by this provider
     */
    String getLabel();

    /**
     * Returns the URL to be navigated to when the user clicks on the category label.
     * <p>
     * This can be left empty if there is no group or overview to navigate to.
     *
     * @return the URL to be used as destination of the category link
     */
    @Nullable
    String getUrl();

    /**
     * Returns the icon to be shown for this provider.
     *
     * @return the icon to be used for this provider
     */
    default String getIcon() {
        return "fa-search";
    }

    /**
     * Ensures that this provider yields results for the current user.
     * <p>
     * This should most probably check if the user has appropriate permissions.
     *
     * @return <tt>true</tt> if the current user may use this provider, <tt>false</tt> otherwise
     */
    boolean ensureAccess();

    /**
     * Actually executes query for this provider.
     *
     * @param query           the query to search by. This is guaranteed to be non-empty and lowercased.
     * @param maxResults      the maximal number of search results to fetch
     * @param resultCollector the collector to pass search results to
     */
    void query(String query, int maxResults, Consumer<OpenSearchResult> resultCollector);
}
