/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.search;

import sirius.kernel.di.std.Priorized;

import java.util.function.Consumer;

public interface OpenSearchProvider extends Priorized {

    String getLabel();

    boolean ensureAccess();

    void query(String query, int maxResults, Consumer<OpenSearchResult> resultCollector);
}
