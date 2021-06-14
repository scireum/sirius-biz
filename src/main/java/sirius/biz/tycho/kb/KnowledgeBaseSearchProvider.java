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
import sirius.web.security.UserContext;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Links the {@link KnowledgeBase} to the {@link sirius.biz.tycho.search.OpenSearchController}.
 * <p>
 * Permits to search in KBAs using the open search facility.
 */
@Register
public class KnowledgeBaseSearchProvider implements OpenSearchProvider {

    @Part
    private KnowledgeBase knowledgeBase;

    @Override
    public String getLabel() {
        return "Knowledgebase";
    }

    @Nullable
    @Override
    public String getUrl() {
        return "/kb";
    }

    @Override
    public boolean ensureAccess() {
        return true;
    }

    @Override
    public void query(String query, int maxResults, Consumer<OpenSearchResult> resultCollector) {
        knowledgeBase.query(UserContext.getCurrentUser().getLang(), query, maxResults).forEach(article -> {
            resultCollector.accept(new OpenSearchResult().withLabel(article.getTitle())
                                                         .withDescription(article.getDescription())
                                                         .withURL("/kb/" + article.getArticleId()));
        });
    }

    @Override
    public int getPriority() {
        return 900;
    }
}
