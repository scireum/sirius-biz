/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

import sirius.biz.web.BizController;
import sirius.kernel.commons.Strings;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Represents an article or chapter in the {@link KnowledgeBase}.
 * <p>
 * This is mainly a wrapper around {@link KnowledgeBaseEntry} which also drags around the effective language
 * which has been used during the lookup.
 * <p>
 * It provides a bunch of convenience methods which are used within the templates.
 */
public class KnowledgeBaseArticle {

    private final String language;
    private final KnowledgeBaseEntry entry;
    private final KnowledgeBase knowledgeBase;

    /**
     * Creates a new article.
     *
     * @param entry         the entry to wrap
     * @param language      the language code which has been used to lookup the article
     * @param knowledgeBase the instance of the knowledge base used to perform further lookups
     */
    public KnowledgeBaseArticle(KnowledgeBaseEntry entry, String language, KnowledgeBase knowledgeBase) {
        this.entry = entry;
        this.language = language;
        this.knowledgeBase = knowledgeBase;
    }

    /**
     * Returns an absolute URL which can be used to view this article without logging in.
     *
     * @return the pre-signed absolute URL to this article
     */
    public String getPresignedUrl() {
        return BizController.getBaseUrl()
               + "/kba/"
               + language
               + "/"
               + getArticleId()
               + "/"
               + computeAuthenticationSignature(true);
    }

    /**
     * Computes the authentication signature.
     *
     * @param thisMonth determines if the signature is computed for the current month (<tt>true</tt>) or the last month
     *                  (<tt>false</tt>)
     * @return the authentication signature for this article
     */
    public String computeAuthenticationSignature(boolean thisMonth) {
        LocalDate date = LocalDate.now();
        if (!thisMonth) {
            date = date.minusMonths(1);
        }

        return Strings.limit(BizController.computeConstantSignature(getArticleId()
                                                                    + date.getYear()
                                                                    + date.getMonthValue()), 5);
    }

    /**
     * Returns all child chapters for this article.
     *
     * @return a list of all child chapters for this article
     */
    public List<KnowledgeBaseArticle> queryChildChapters() {
        return knowledgeBase.queryChildChapters(this);
    }

    /**
     * Returns all child articles for this article.
     *
     * @return a list of all child articles for this article
     */
    public List<KnowledgeBaseArticle> queryChildren() {
        return knowledgeBase.queryChildArticles(this);
    }

    /**
     * Returns a list of all cross references.
     *
     * @return a list of all articles which are either referenced by this article or which reference this article
     * by themselves.
     */
    public List<KnowledgeBaseArticle> queryCrossReferences() {
        return knowledgeBase.queryCrossReferences(this);
    }

    /**
     * Tries to resolve the parent article.
     *
     * @return the parent of this article, or an empty optional if the current article is already the root chapter or
     * not placed in any chapter at all.
     */
    public Optional<KnowledgeBaseArticle> queryParent() {
        return knowledgeBase.resolve(language, entry.getParentId(), false);
    }

    /**
     * Tries to resolve the parent structure of this article.
     *
     * @return a list of the roots of this article (ordered from near to far), or an empty list if the current article
     * is already the root chapter or not placed in any chapter at all.
     */
    public List<KnowledgeBaseArticle> queryParents() {
        List<KnowledgeBaseArticle> parents = new ArrayList<>();

        KnowledgeBaseArticle parent = queryParent().orElse(null);
        while (parent != null) {
            parents.addFirst(parent);
            parent = parent.queryParent().orElse(null);
        }

        return parents;
    }

    public String getArticleId() {
        return entry.getArticleId();
    }

    public String getTitle() {
        return entry.getTitle();
    }

    public String getDescription() {
        return entry.getDescription();
    }

    public String getTemplatePath() {
        return entry.getTemplatePath();
    }

    public String getLanguage() {
        return language;
    }

    protected KnowledgeBaseEntry getEntry() {
        return entry;
    }

    public boolean isChapter() {
        return entry.isChapter();
    }

    public int getPriority() {
        return entry.getPriority();
    }
}
