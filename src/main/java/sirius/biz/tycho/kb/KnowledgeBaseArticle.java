/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

import java.util.List;

public class KnowledgeBaseArticle {

    protected KnowledgeBaseEntry entry;
    private KnowledgeBase knowledgeBase;

    public KnowledgeBaseArticle(KnowledgeBaseEntry entry, KnowledgeBase knowledgeBase) {
        this.entry = entry;
        this.knowledgeBase = knowledgeBase;
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
        return entry.getLang();
    }

    public List<KnowledgeBaseArticle> queryChildChapters() {
        return knowledgeBase.queryChildChapters(this);
    }

    public List<KnowledgeBaseArticle> queryChildren() {
        return knowledgeBase.queryChildren(this);
    }

    public List<KnowledgeBaseArticle> queryCrossReferences() {
        return knowledgeBase.queryCrossReferences(this);
    }

    public List<KnowledgeBaseArticle> queryBreadcrumbs() {
        return knowledgeBase.queryBreadcrumbs(this);
    }



}
