/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

import sirius.biz.elastic.SearchContent;
import sirius.biz.elastic.SearchableEntity;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.types.StringList;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Priorized;
import sirius.web.security.UserContext;

import java.time.LocalDate;

/**
 * Represents a knowledge base article which is stored in Elasticsearch.
 */
@Framework(KnowledgeBase.FRAMEWORK_KNOWLEDGE_BASE)
public class KnowledgeBaseEntry extends SearchableEntity {

    /**
     * Contains the five-letter code of this article.
     */
    public static final Mapping ARTICLE_ID = Mapping.named("articleId");
    @SearchContent
    private String articleId;

    /**
     * Contains the five-letter code if the parent of this article.
     */
    public static final Mapping PARENT_ID = Mapping.named("parentId");
    @NullAllowed
    private String parentId;

    /**
     * Contains the sort priority of this article.
     */
    public static final Mapping PRIORITY = Mapping.named("priority");
    private int priority = Priorized.DEFAULT_PRIORITY;

    /**
     * Contains the required permissions of this article.
     */
    public static final Mapping REQUIRED_PERMISSIONS = Mapping.named("requiredPermissions");
    @NullAllowed
    private String requiredPermissions;

    /**
     * Contains a list of file-letter codes which reference or are referenced by this article.
     */
    public static final Mapping RELATES_TO = Mapping.named("relatesTo");
    private final StringList relatesTo = new StringList();

    /**
     * Contains the date when this article was first seen / created.
     */
    public static final Mapping CREATED = Mapping.named("created");
    private LocalDate created;

    /**
     * Contains the version number which introduced this article.
     */
    public static final Mapping CREATED_IN_VERSION = Mapping.named("createdInVersion");
    private String createdInVersion;

    /**
     * Determines if this article is a chapter or a "normal" article.
     */
    public static final Mapping CHAPTER = Mapping.named("chapter");
    private boolean chapter;

    /**
     * Contains the two-letter language code of this article.
     */
    public static final Mapping LANGUAGE = Mapping.named("language");
    private String language;

    /**
     * Contains the title of this article.
     */
    public static final Mapping TITLE = Mapping.named("title");
    @SearchContent
    private String title;

    /**
     * Contains a short and concise description of this article.
     */
    public static final Mapping DESCRIPTION = Mapping.named("description");
    @SearchContent
    private String description;

    /**
     * Contains the path of the template for which this entry has been created.
     */
    public static final Mapping TEMPLATE_PATH = Mapping.named("templatePath");
    private String templatePath;

    /**
     * Contains a random synchronization id used by {@link SynchronizeArticlesTask} to detect outdated articles.
     */
    public static final Mapping SYNC_ID = Mapping.named("syncId");
    private String syncId;

    @Override
    public String toString() {
        return getArticleId() + ": " + getTitle();
    }

    /**
     * Checks if the permissions required by this article are met.
     *
     * @return <tt>true</tt> if the current user may view this article, <tt>false</tt> otherwise
     */
    public boolean checkPermissions() {
        if (Strings.isEmpty(requiredPermissions)) {
            return true;
        }

        return UserContext.getCurrentUser().hasPermission(requiredPermissions);
    }

    public String getArticleId() {
        return articleId;
    }

    public void setArticleId(String articleId) {
        this.articleId = articleId;
    }

    public boolean isChapter() {
        return chapter;
    }

    public void setChapter(boolean chapter) {
        this.chapter = chapter;
    }

    @Deprecated
    public String getLang() {
        return language;
    }

    @Deprecated
    public void setLang(String lang) {
        this.language = lang;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTemplatePath() {
        return templatePath;
    }

    public void setTemplatePath(String templatePath) {
        this.templatePath = templatePath;
    }

    public String getSyncId() {
        return syncId;
    }

    public void setSyncId(String syncId) {
        this.syncId = syncId;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public StringList getRelatesTo() {
        return relatesTo;
    }

    public String getRequiredPermissions() {
        return requiredPermissions;
    }

    public void setRequiredPermissions(String requiredPermissions) {
        this.requiredPermissions = requiredPermissions;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public LocalDate getCreated() {
        return created;
    }

    public void setCreated(LocalDate created) {
        this.created = created;
    }

    public String getCreatedInVersion() {
        return createdInVersion;
    }

    public void setCreatedInVersion(String createdInVersion) {
        this.createdInVersion = createdInVersion;
    }
}
