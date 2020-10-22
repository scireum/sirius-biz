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

@Framework(KnowledgeBase.FRAMEWORK_KNOWLEDGE_BASE)
public class KnowledgeBaseEntry extends SearchableEntity {

    public static final Mapping ARTICLE_ID = Mapping.named("articleId");
    private String articleId;

    public static final Mapping PARENT_ID = Mapping.named("parentId");
    @NullAllowed
    private String parentId;

    public static final Mapping PRIORITY = Mapping.named("priority");
    private int priority = Priorized.DEFAULT_PRIORITY;

    public static final Mapping REQUIRED_PERMISSIONS = Mapping.named("requiredPermissions");
    @NullAllowed
    private String requiredPermissions;

    public static final Mapping RELATES_TO = Mapping.named("relatesTo");
    private final StringList relatesTo = new StringList();

    public static final Mapping CHAPTER = Mapping.named("chapter");
    private boolean chapter;

    public static final Mapping LANG = Mapping.named("lang");
    private String lang;

    public static final Mapping TITLE = Mapping.named("title");
    @SearchContent
    private String title;

    public static final Mapping DESCRIPTION = Mapping.named("description");
    @SearchContent
    private String description;

    public static final Mapping TEMPLATEPATH = Mapping.named("templatePath");
    private String templatePath;

    public static final Mapping SYNC_ID = Mapping.named("syncId");
    private String syncId;

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

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
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
}
