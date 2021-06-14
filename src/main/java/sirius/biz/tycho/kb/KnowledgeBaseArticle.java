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

    private final String lang;
    private final KnowledgeBaseEntry entry;
    private final KnowledgeBase knowledgeBase;

    /**
     * Creates a new article.
     *
     * @param entry         the entry to wrap
     * @param lang          the language code which has been used to lookup the article
     * @param knowledgeBase the instance of the knowledge base used to perform further lookups
     */
    public KnowledgeBaseArticle(KnowledgeBaseEntry entry, String lang, KnowledgeBase knowledgeBase) {
        this.entry = entry;
        this.lang = lang;
        this.knowledgeBase = knowledgeBase;
    }

    public int getPriority() {
        return entry.getPriority();
    }

    /**
     * Returns an absolute URL which can be used to view this article without logging in.
     *
     * @return the pre-signed absolute URL to this article
     */
    public String getPresignedUrl() {
        return BizController.getBaseUrl() + "/kba/" + lang + "/" + getArticleId() + "/" + getAuthSignature(true);
    }

    /**
     * Computes the authentication signature.
     *
     * @param thisMonth determines if the signature is computed for the current month (<tt>true</tt>) or the last month
     *                  (<tt>false</tt>)
     * @return the authentication signature for this article
     */
    public String getAuthSignature(boolean thisMonth) {
        LocalDate date = LocalDate.now();
        if (!thisMonth) {
            date = date.minusMonths(1);
        }

        return Strings.limit(BizController.computeURISignature(getArticleId() + date.getYear() + date.getMonthValue()),
                             5);
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
        return knowledgeBase.resolve(lang, entry.getParentId(), false);
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

    public String getLang() {
        return lang;
    }

    protected KnowledgeBaseEntry getEntry() {
        return entry;
    }

    public boolean isChapter() {
        return entry.isChapter();
    }
}
