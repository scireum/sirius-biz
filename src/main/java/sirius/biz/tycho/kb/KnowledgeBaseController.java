/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.analytics.events.EventRecorder;
import sirius.biz.analytics.events.PageImpressionEvent;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.web.BizController;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.controller.AutocompleteHelper;
import sirius.web.controller.Routed;
import sirius.web.http.MimeHelper;
import sirius.web.http.Response;
import sirius.web.http.WebContext;
import sirius.web.resources.Resources;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;

import java.io.IOException;
import java.net.URLConnection;
import java.time.Duration;
import java.util.List;

/**
 * Provides the UI of the knowledge base.
 */
@Register(framework = KnowledgeBase.FRAMEWORK_KNOWLEDGE_BASE)
public class KnowledgeBaseController extends BizController {

    @Part
    private KnowledgeBase knowledgeBase;

    @Part
    private EventRecorder eventRecorder;

    @Part
    private Resources resources;

    @ConfigValue("http.response.defaultStaticAssetTTL")
    private static Duration defaultStaticAssetTTL;

    /**
     * Renders the entry point of the knowledge base in the current language.
     *
     * @param webContext the current request to respond to
     */
    @Routed("/kb")
    public void kb(WebContext webContext) {
        langArticle(webContext, NLS.getCurrentLanguage(), KnowledgeBase.ROOT_CHAPTER_ID);
    }

    /**
     * Redirects to the entry point of the knowledge base in the current language.
     * <p>
     * Sometimes, users may open the wrong URL. Therefore, we redirect to the correct one.
     *
     * @param webContext the current request to respond to
     */
    @Routed("/kba")
    public void kbRedirect(WebContext webContext) {
        webContext.respondWith().redirectPermanently("/kb");
    }

    /**
     * Renders the entry point of the knowledge base in the given language.
     *
     * @param webContext the current request to respond to
     * @param language   the language to open the knowledge base in
     */
    @Routed("/kb/:1")
    public void langKb(WebContext webContext, String language) {
        langArticle(webContext, language, KnowledgeBase.ROOT_CHAPTER_ID);
    }

    /**
     * Delivers static assets from within the knowledge base file structure.
     * <p>
     * This route currently only delivers images.
     *
     * @param webContext the current request to respond to
     * @param subPath    the sub path of the requested asset
     */
    @Routed(value = "/kb/**", priority = 200)
    public void deliverAsset(WebContext webContext, List<String> subPath) {
        resources.resolve("/kb/" + Strings.join(subPath, "/"))
                 .filter(resource -> MimeHelper.isProbablyAnImage(MimeHelper.guessMimeType(subPath.getLast())))
                 .ifPresentOrElse(resource -> {
                     try {
                         URLConnection urlConnection = resource.getUrl().openConnection();
                         Response response = webContext.respondWith();
                         if (!response.handleIfModifiedSince(urlConnection.getLastModified())) {
                             response.cachedForSeconds((int) defaultStaticAssetTTL.getSeconds())
                                     .resource(urlConnection);
                         }
                     } catch (IOException exception) {
                         throw Exceptions.handle(exception);
                     }
                 }, () -> webContext.respondWith().error(HttpResponseStatus.NOT_FOUND));
    }

    /**
     * Renders the requested article in the current language of the user.
     *
     * @param webContext the current request to respond to
     * @param articleId  the id or code of the article to render
     */
    @Routed("/kba/:1")
    public void article(WebContext webContext, String articleId) {
        langArticle(webContext, NLS.getCurrentLanguage(), articleId);
    }

    /**
     * Renders the requested article in the given language.
     *
     * @param webContext the current request to respond to
     * @param language   the language to render the article in
     * @param articleId  the id or code of the article to render
     */
    @Routed("/kba/:1/:2")
    public void langArticle(WebContext webContext, String language, String articleId) {
        langArticle(webContext, language, articleId, null);
    }

    /**
     * Renders the pre-authorized article in the given language.
     *
     * @param webContext the current request to respond to
     * @param language   the language to render the article in
     * @param articleId  the id or code of the article to render
     * @param authKey    the authentication signature to verify
     */
    @Routed("/kba/:1/:2/:3")
    public void langArticle(WebContext webContext, String language, String articleId, String authKey) {
        KnowledgeBaseArticle article = knowledgeBase.resolve(language, articleId, true).orElse(null);
        if (article == null) {
            webContext.respondWith().error(HttpResponseStatus.NOT_FOUND);
            return;
        }

        if (!article.getEntry().checkPermissions() && !checkAuthKey(article, authKey)) {
            if (!UserContext.getCurrentUser().isLoggedIn()) {
                webContext.respondWith().template("/templates/biz/login.html.pasta", webContext.getRequest().uri());
            } else {
                throw Exceptions.createHandled().withNLSKey("KnowledgeBase.missingPermission").handle();
            }
            return;
        }

        UserContext.getHelper(KBHelper.class).installCurrentArticle(article);
        try {
            webContext.respondWith().template(article.getTemplatePath());
            eventRecorder.record(new PageImpressionEvent().withUri("/kba/"
                                                                   + article.getLanguage()
                                                                   + "/"
                                                                   + article.getArticleId())
                                                          .withAggregationUrl("/kba")
                                                          .withAction(article.getArticleId())
                                                          .withDataObject(article.getEntry().getUniqueName()));
        } finally {
            UserContext.getHelper(KBHelper.class).clearCurrentArticle();
        }
    }

    private boolean checkAuthKey(KnowledgeBaseArticle article, String authKey) {
        if (Strings.isEmpty(authKey)) {
            return false;
        }
        return Strings.areEqual(article.computeAuthenticationSignature(true), authKey)
               || Strings.areEqual(article.computeAuthenticationSignature(false), authKey);
    }

    /**
     * Provides an autocompletion for KB articles.
     *
     * @param webContext the current request
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT_MEMBER)
    @Routed(value = "/kb/autocomplete", priority = 99)
    public void articlesAutocomplete(final WebContext webContext) {
        AutocompleteHelper.handle(webContext, (query, result) -> {
            knowledgeBase.query(null, query, AutocompleteHelper.DEFAULT_LIMIT).forEach(article -> {
                result.accept(AutocompleteHelper.suggest(article.getEntry().getIdAsString())
                                                .withCompletionLabel(article.getArticleId() + ": " + article.getTitle())
                                                .withCompletionDescription(article.getDescription()));
            });
        });
    }
}
