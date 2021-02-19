/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

import sirius.db.es.Elastic;
import sirius.db.es.ElasticQuery;
import sirius.db.mixing.query.QueryField;
import sirius.kernel.commons.Lambdas;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Register(classes = KnowledgeBase.class)
public class KnowledgeBase {

    public static final String FRAMEWORK_KNOWLEDGE_BASE = "tycho.knowledge-base";

    public static final String ROOT_CHAPTER_ID = "00000";

    public static final Log LOG = Log.get("knowlegdebase");

    @Part
    private Elastic elastic;

    private ThreadLocal<KnowledgeBaseArticle> currentArticle = new ThreadLocal<>();

    public KnowledgeBaseArticle currentArticle() {
        return currentArticle.get();
    }

    public void installCurrentArticle(KnowledgeBaseArticle article) {
        currentArticle.set(article);
    }

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

    protected List<KnowledgeBaseEntry> search(String lang, Consumer<ElasticQuery<KnowledgeBaseEntry>> queryDecorator) {
        List<KnowledgeBaseEntry> result = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();
        ElasticQuery<KnowledgeBaseEntry> query =
                elastic.select(KnowledgeBaseEntry.class).eq(KnowledgeBaseEntry.LANG, lang);
        queryDecorator.accept(query);
        query.orderAsc(KnowledgeBaseEntry.PRIORITY).orderAsc(KnowledgeBaseEntry.TITLE).iterateAll(entry -> {
            if (entry.checkPermissions()) {
                result.add(entry);
                seenCodes.add(entry.getArticleId());
            }
        });

        for (String fallbackLanguage : Arrays.asList("de", "en")) {
            if (!Strings.areEqual(fallbackLanguage, lang)) {
                query = elastic.select(KnowledgeBaseEntry.class).eq(KnowledgeBaseEntry.LANG, fallbackLanguage);
                queryDecorator.accept(query);
                query.orderAsc(KnowledgeBaseEntry.PRIORITY).orderAsc(KnowledgeBaseEntry.TITLE).iterateAll(entry -> {
                    if (entry.checkPermissions() && !seenCodes.contains(entry.getArticleId())) {
                        result.add(entry);
                        seenCodes.add(entry.getArticleId());
                    }
                });
            }
        }

        return result;
    }

    protected List<KnowledgeBaseArticle> queryChildChapters(KnowledgeBaseArticle kba) {
        return search(kba.getLang(),
                      query -> query.eq(KnowledgeBaseEntry.PARENT_ID, kba.getArticleId())
                                    .eq(KnowledgeBaseEntry.CHAPTER, true)).stream()
                                                                          .map(entry -> new KnowledgeBaseArticle(entry,
                                                                                                                 this))
                                                                          .collect(Collectors.toList());
    }

    protected List<KnowledgeBaseArticle> queryChildren(KnowledgeBaseArticle kba) {
        return search(kba.getLang(),
                      query -> query.eq(KnowledgeBaseEntry.PARENT_ID, kba.getArticleId())
                                    .eq(KnowledgeBaseEntry.CHAPTER, false)).stream()
                                                                           .map(entry -> new KnowledgeBaseArticle(entry,
                                                                                                                  this))
                                                                           .collect(Collectors.toList());
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

        List<KnowledgeBaseEntry> externalReferences =
                search(kba.getLang(), query -> query.eq(KnowledgeBaseEntry.RELATES_TO, kba.getArticleId()));
        externalReferences.stream().map(entry -> new KnowledgeBaseArticle(entry, this)).collect(Lambdas.into(result));

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
