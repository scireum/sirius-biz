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
import sirius.kernel.nls.NLS;
import sirius.web.security.UserContext;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Links the {@link KnowledgeBase} to the {@link sirius.biz.tycho.search.OpenSearchController}.
 * <p>
 * Permits searching in {@link KnowledgeBaseEntry knowledge base articles} using the open search facility.
 */
@Register(framework = KnowledgeBase.FRAMEWORK_KNOWLEDGE_BASE)
public class KnowledgeBaseSearchProvider implements OpenSearchProvider {

    @Part
    private KnowledgeBase knowledgeBase;

    @Override
    public String getLabel() {
        return NLS.get("KnowledgeBase.kb");
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
        knowledgeBase.query(UserContext.getCurrentUser().getLanguage(), query, maxResults).forEach(article -> {
            KnowledgeBaseArticle manual = article.queryParent().orElse(null);
            while (!KnowledgeBase.ROOT_CHAPTER_ID.equals(manual.queryParent().orElse(null).getArticleId())) {
                manual = manual.queryParent().orElse(null);
            }
            resultCollector.accept(new OpenSearchResult().withLabel(article.getTitle())
                                                         .withDescription(article.getDescription())
                                                         .withURL("/kba/" + article.getLanguage()+ "/" + article.getArticleId()));
        });
    }

    @Override
    public int getPriority() {
        return 900;
    }
}
