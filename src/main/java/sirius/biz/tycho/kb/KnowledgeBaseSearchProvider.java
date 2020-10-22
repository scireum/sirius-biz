/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

import sirius.biz.tycho.search.OpenSearchProvider;
import sirius.biz.tycho.search.OpenSearchResult;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import java.util.function.Consumer;

@Register
public class KnowledgeBaseSearchProvider implements OpenSearchProvider {

    @Part
    private KnowledgeBase knowledgeBase;

    @Override
    public String getLabel() {
        return "Knowledgebase";
    }

    @Override
    public boolean ensureAccess() {
        return true;
    }

    @Override
    public void query(String query, int maxResults, Consumer<OpenSearchResult> resultCollector) {
        knowledgeBase.query("de", query,maxResults).forEach(article -> {
            resultCollector.accept(new OpenSearchResult(article.getTitle(),
                                                        article.getDescription(),
                                                        "/tycho/kba/" + article.getArticleId()));
        });
    }

    @Override
    public int getPriority() {
        return 900;
    }
}
