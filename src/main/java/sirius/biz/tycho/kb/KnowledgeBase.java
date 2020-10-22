/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

import sirius.db.es.Elastic;
import sirius.db.mixing.query.QueryField;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Register(classes = KnowledgeBase.class)
public class KnowledgeBase {

    public static final String FRAMEWORK_KNOWLEDGE_BASE = "tycho.knowledge-base";

    public static final String ROOT_CHAPTER_ID = "00000";

    public static final Log LOG = Log.get("knowlegdebase");

    @Part
    private Elastic elastic;

    public Optional<KnowledgeBaseArticle> resolve(String lang, String kbaId) {
        if (Strings.isEmpty(kbaId)) {
            return Optional.empty();
        }

        return elastic.select(KnowledgeBaseEntry.class)
                      .eq(KnowledgeBaseEntry.LANG, lang)
                      .eq(KnowledgeBaseEntry.ARTICLE_ID, kbaId.toUpperCase())
                      .first()
                      .filter(KnowledgeBaseEntry::checkPermissions)
                      .map(entry -> new KnowledgeBaseArticle(entry, this));
    }

    protected List<KnowledgeBaseArticle> queryChildChapters(KnowledgeBaseArticle kba) {
        List<KnowledgeBaseArticle> result = new ArrayList<>();
        elastic.select(KnowledgeBaseEntry.class)
               .eq(KnowledgeBaseEntry.LANG, kba.getLang())
               .eq(KnowledgeBaseEntry.PARENT_ID, kba.getArticleId())
               .eq(KnowledgeBaseEntry.CHAPTER, true)
               .orderAsc(KnowledgeBaseEntry.PRIORITY)
               .orderAsc(KnowledgeBaseEntry.TITLE)
               .iterateAll(entry -> {
                   if (entry.checkPermissions()) {
                       result.add(new KnowledgeBaseArticle(entry, this));
                   }
               });

        return result;
    }

    protected List<KnowledgeBaseArticle> queryChildren(KnowledgeBaseArticle kba) {
        List<KnowledgeBaseArticle> result = new ArrayList<>();
        elastic.select(KnowledgeBaseEntry.class)
               .eq(KnowledgeBaseEntry.LANG, kba.getLang())
               .eq(KnowledgeBaseEntry.PARENT_ID, kba.getArticleId())
               .eq(KnowledgeBaseEntry.CHAPTER, false)
               .orderAsc(KnowledgeBaseEntry.PRIORITY)
               .orderAsc(KnowledgeBaseEntry.TITLE)
               .iterateAll(entry -> {
                   if (entry.checkPermissions()) {
                       result.add(new KnowledgeBaseArticle(entry, this));
                   }
               });

        return result;
    }

    protected List<KnowledgeBaseArticle> queryCrossReferences(KnowledgeBaseArticle kba) {
        List<KnowledgeBaseArticle> result = new ArrayList<>();
        kba.entry.getRelatesTo()
                 .data()
                 .stream()
                 .map(articleId -> resolve(kba.getLang(), articleId))
                 .filter(Optional::isPresent)
                 .map(Optional::get)
                 .forEach(result::add);

        elastic.select(KnowledgeBaseEntry.class)
               .eq(KnowledgeBaseEntry.LANG, kba.getLang())
               .eq(KnowledgeBaseEntry.RELATES_TO, kba.getArticleId())
               .iterateAll(entry -> {
                   if (!kba.entry.getRelatesTo().contains(entry.getArticleId()) && entry.checkPermissions()) {
                       result.add(new KnowledgeBaseArticle(entry, this));
                   }
               });

        result.sort(Comparator.<KnowledgeBaseArticle, Integer>comparing(article -> article.entry.getPriority()).thenComparing(
                KnowledgeBaseArticle::getTitle));

        return result;
    }

    public List<KnowledgeBaseArticle> queryBreadcrumbs(KnowledgeBaseArticle kba) {
        List<KnowledgeBaseArticle> result = new ArrayList<>();

        String parentId = kba.entry.getParentId();
        while (Strings.isFilled(parentId) && !ROOT_CHAPTER_ID.equals(parentId)) {
            kba = resolve(kba.getLang(), parentId).orElse(null);
            if (kba != null) {
                result.add(0, kba);
                parentId = kba.entry.getParentId();
            } else {
                parentId = null;
            }
        }

        return result;
    }

    public List<KnowledgeBaseArticle> query(String lang, String query, int maxResults) {
        List<KnowledgeBaseArticle> result = new ArrayList<>();
        elastic.select(KnowledgeBaseEntry.class)
               .eq(KnowledgeBaseEntry.LANG, lang)
               .queryString(query, QueryField.contains(KnowledgeBaseEntry.SEARCH_FIELD))
               .limit(maxResults * 5)
               .iterateAll(entry -> {
                   if (entry.checkPermissions()) {
                       result.add(new KnowledgeBaseArticle(entry, this));
                   }
               });

        return result;
    }
}
