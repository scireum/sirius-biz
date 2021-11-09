/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

import sirius.db.es.AggregationBuilder;
import sirius.db.es.Bucket;
import sirius.db.es.Elastic;
import sirius.db.es.ElasticQuery;
import sirius.db.mixing.query.QueryField;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Provides access to all available knowledge base articles.
 */
@Register(classes = KnowledgeBase.class)
public class KnowledgeBase {

    /**
     * Specifies the framework name which is used to enable the knowledge base.
     */
    public static final String FRAMEWORK_KNOWLEDGE_BASE = "tycho.knowledge-base";

    /**
     * Specifies the chapter code which is rendered if the KB is first opened.
     */
    public static final String ROOT_CHAPTER_ID = "00000";

    /**
     * Used to log all messages and warnings related to the knowledge base.
     */
    public static final Log LOG = Log.get("knowlegdebase");

    /**
     * Contains the default or fallback language which is considered additionally to the current language.
     */
    @ConfigValue("knowledgebase.fallbackLang")
    protected String fallbackLang;

    @Part
    private Elastic elastic;
    private List<String> languages;

    /**
     * Resolves the code into an article.
     *
     * @param language            the language to resolve the article for. Note that this will also consider the
     *                            {@link #fallbackLang}
     * @param articleId           the code or id of the article to resolve
     * @param skipPermissionCheck determines if the permissions for the article should be enforced (<tt>false</tt>)
     *                            or skipped (<tt>true</tt>).
     * @return the resolved article, or an empty optional if the article doesn't exist or if the current user isn't
     * permitted to view it
     */
    public Optional<KnowledgeBaseArticle> resolve(String language, String articleId, boolean skipPermissionCheck) {
        if (Strings.isEmpty(articleId)) {
            return Optional.empty();
        }
        if (!Sirius.isFrameworkEnabled(FRAMEWORK_KNOWLEDGE_BASE)) {
            return Optional.empty();
        }

        Optional<KnowledgeBaseEntry> candidate = elastic.select(KnowledgeBaseEntry.class)
                                                        .eq(KnowledgeBaseEntry.LANG, language)
                                                        .eq(KnowledgeBaseEntry.ARTICLE_ID, articleId.toUpperCase())
                                                        .first();
        if (!Strings.areEqual(language, fallbackLang)) {
            candidate = candidate.or(() -> elastic.select(KnowledgeBaseEntry.class)
                                                  .eq(KnowledgeBaseEntry.LANG, fallbackLang)
                                                  .eq(KnowledgeBaseEntry.ARTICLE_ID, articleId.toUpperCase())
                                                  .first());
        }
        if (!skipPermissionCheck) {
            candidate = candidate.filter(KnowledgeBaseEntry::checkPermissions);
        }
        return candidate.map(entry -> new KnowledgeBaseArticle(entry, language, this));
    }

    /**
     * Determines the fallback or default lang of the knowledge base.
     *
     * @return the default language as two-letter ISO code
     */
    public String getFallbackLang() {
        return fallbackLang;
    }

    /**
     * Queries the list of all languages for which at least one article is present.
     *
     * @return the list of languages (two-letter ISO codes) for which articles are present
     */
    public List<String> queryLanguages() {
        if (languages == null) {
            if (Sirius.isFrameworkEnabled(FRAMEWORK_KNOWLEDGE_BASE)) {
                ElasticQuery<KnowledgeBaseEntry> query = elastic.select(KnowledgeBaseEntry.class)
                                                                .addAggregation(AggregationBuilder.createTerms(
                                                                        KnowledgeBaseEntry.LANG));
                query.computeAggregations();
                languages = query.getAggregation(KnowledgeBaseEntry.LANG.getName())
                                 .getBuckets()
                                 .stream()
                                 .map(Bucket::getKey)
                                 .sorted()
                                 .collect(Collectors.toList());
            } else {
                languages = Collections.emptyList();
            }
        }

        return Collections.unmodifiableList(languages);
    }

    protected void resetLanguages() {
        this.languages = null;
    }

    /**
     * Queries the list of language codes in which a given article is available.
     *
     * @param articleId the id or code of the article to check
     * @return the list of two-letter ISO codes in which this article is available
     */
    public List<String> queryLanguageVersions(String articleId) {
        if (!Sirius.isFrameworkEnabled(FRAMEWORK_KNOWLEDGE_BASE)) {
            return Collections.emptyList();
        }

        return elastic.select(KnowledgeBaseEntry.class)
                      .eq(KnowledgeBaseEntry.ARTICLE_ID, articleId.toUpperCase())
                      .orderAsc(KnowledgeBaseEntry.LANG)
                      .queryList()
                      .stream()
                      .map(KnowledgeBaseEntry::getLang)
                      .sorted()
                      .collect(Collectors.toList());
    }

    protected List<KnowledgeBaseArticle> queryChildChapters(KnowledgeBaseArticle article) {
        return queryChildren(article, true);
    }

    protected List<KnowledgeBaseArticle> queryChildArticles(KnowledgeBaseArticle article) {
        return queryChildren(article, false);
    }

    private List<KnowledgeBaseArticle> queryChildren(KnowledgeBaseArticle article, boolean chapter) {
        List<KnowledgeBaseArticle> result = new ArrayList<>();
        elastic.select(KnowledgeBaseEntry.class)
               .eq(KnowledgeBaseEntry.LANG, article.getLanguage())
               .eq(KnowledgeBaseEntry.PARENT_ID, article.getArticleId())
               .eq(KnowledgeBaseEntry.CHAPTER, chapter)
               .iterateAll(entry -> {
                   if (entry.checkPermissions()) {
                       result.add(new KnowledgeBaseArticle(entry, article.getLanguage(), this));
                   }
               });

        if (!Strings.areEqual(article.getLanguage(), fallbackLang)) {
            elastic.select(KnowledgeBaseEntry.class)
                   .eq(KnowledgeBaseEntry.LANG, fallbackLang)
                   .eq(KnowledgeBaseEntry.PARENT_ID, article.getArticleId())
                   .eq(KnowledgeBaseEntry.CHAPTER, chapter)
                   .iterateAll(entry -> {
                       if (entry.checkPermissions() && result.stream()
                                                             .noneMatch(existingEntry -> Strings.areEqual(existingEntry.getArticleId(),
                                                                                                          entry.getArticleId()))) {
                           result.add(new KnowledgeBaseArticle(entry, article.getLanguage(), this));
                       }
                   });
        }

        result.sort(Comparator.comparing(KnowledgeBaseArticle::getPriority)
                              .thenComparing(KnowledgeBaseArticle::getTitle));

        return result;
    }

    protected List<KnowledgeBaseArticle> queryCrossReferences(KnowledgeBaseArticle article) {
        List<KnowledgeBaseArticle> result = new ArrayList<>();
        article.getEntry()
               .getRelatesTo()
               .data()
               .stream()
               .map(articleId -> resolve(article.getLanguage(), articleId, false))
               .flatMap(Optional::stream)
               .forEach(result::add);

        elastic.select(KnowledgeBaseEntry.class)
               .eq(KnowledgeBaseEntry.LANG, article.getLanguage())
               .eq(KnowledgeBaseEntry.RELATES_TO, article.getArticleId())
               .iterateAll(entry -> {
                   if (entry.checkPermissions() && result.stream()
                                                         .noneMatch(existingEntry -> Strings.areEqual(existingEntry.getArticleId(),
                                                                                                      entry.getArticleId()))) {
                       result.add(new KnowledgeBaseArticle(entry, article.getLanguage(), this));
                   }
               });

        if (!Strings.areEqual(article.getLanguage(), fallbackLang)) {
            elastic.select(KnowledgeBaseEntry.class)
                   .eq(KnowledgeBaseEntry.LANG, fallbackLang)
                   .eq(KnowledgeBaseEntry.RELATES_TO, article.getArticleId())
                   .iterateAll(entry -> {
                       if (entry.checkPermissions() && result.stream()
                                                             .noneMatch(existingEntry -> Strings.areEqual(existingEntry.getArticleId(),
                                                                                                          entry.getArticleId()))) {
                           result.add(new KnowledgeBaseArticle(entry, article.getLanguage(), this));
                       }
                   });
        }

        result.sort(Comparator.comparing(KnowledgeBaseArticle::getPriority)
                              .thenComparing(KnowledgeBaseArticle::getTitle));

        return result;
    }

    /**
     * Tries to find matching articles for the given query.
     *
     * @param language   the language to query in (the fallback language is also considered)
     * @param query      the query to perform (the keyword to search for)
     * @param maxResults the maximal number of results to fetch
     * @return a list of matching articles
     */
    public List<KnowledgeBaseArticle> query(String language, String query, int maxResults) {
        if (!Sirius.isFrameworkEnabled(FRAMEWORK_KNOWLEDGE_BASE)) {
            return Collections.emptyList();
        }

        List<KnowledgeBaseArticle> result = new ArrayList<>();
        elastic.select(KnowledgeBaseEntry.class)
               .eq(KnowledgeBaseEntry.LANG, language)
               .queryString(query, QueryField.contains(KnowledgeBaseEntry.SEARCH_FIELD))
               .limit(maxResults * 5)
               .iterateAll(entry -> {
                   if (entry.checkPermissions()) {
                       result.add(new KnowledgeBaseArticle(entry, language, this));
                   }
               });
        if (!Strings.areEqual(language, fallbackLang)) {
            elastic.select(KnowledgeBaseEntry.class)
                   .eq(KnowledgeBaseEntry.LANG, language)
                   .queryString(query, QueryField.contains(KnowledgeBaseEntry.SEARCH_FIELD))
                   .limit(maxResults * 5)
                   .iterateAll(entry -> {
                       if (entry.checkPermissions() && result.stream()
                                                             .noneMatch(existingEntry -> Strings.areEqual(existingEntry.getArticleId(),
                                                                                                          entry.getArticleId()))) {
                           result.add(new KnowledgeBaseArticle(entry, language, this));
                       }
                   });
        }

        result.sort(Comparator.comparing(KnowledgeBaseArticle::getPriority)
                              .thenComparing(KnowledgeBaseArticle::getTitle));

        return result;
    }
}
