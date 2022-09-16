/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

/**
 * Provides access to the currently selected {@link KnowledgeBaseArticle} without passing it in as a parameter.
 * <p>
 * We use a thread local mainly to simplify the article templates. Our approach is that the KB detects and uses
 * articles all by itself. Therefore, it would be strange to require an additional parameter for each template
 * which receives "itself".
 */
public class KBHelper {

    private final ThreadLocal<KnowledgeBaseArticle> currentArticle = new ThreadLocal<>();

    /**
     * Provides access to the articles currently being rendered by the {@link KnowledgeBaseController}.
     *
     * @return the article currently being rendered
     */
    public KnowledgeBaseArticle currentArticle() {
        return currentArticle.get();
    }

    protected void installCurrentArticle(KnowledgeBaseArticle article) {
        currentArticle.set(article);
    }

    protected void clearCurrentArticle() {
        currentArticle.remove();
    }
}
