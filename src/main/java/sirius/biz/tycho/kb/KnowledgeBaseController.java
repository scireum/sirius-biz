/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.web.BizController;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.security.UserContext;

/**
 * Provides the UI of the knowledge base.
 */
@Register(framework = KnowledgeBase.FRAMEWORK_KNOWLEDGE_BASE)
public class KnowledgeBaseController extends BizController {

    @Part
    private KnowledgeBase knowledgeBase;

    /**
     * Renders the entry point of the knowledge base in the current language.
     *
     * @param webContext the current request to respond to
     */
    @Routed("/kb")
    public void kb(WebContext webContext) {
        langArticle(webContext, NLS.getCurrentLang(), KnowledgeBase.ROOT_CHAPTER_ID);
    }

    /**
     * Renders the entry point of the knowledge base in the given language.
     *
     * @param webContext the current request to respond to
     * @param lang       the language top open the knowledge base in
     */
    @Routed("/kb/:1")
    public void langKb(WebContext webContext, String lang) {
        langArticle(webContext, lang, KnowledgeBase.ROOT_CHAPTER_ID);
    }

    /**
     * Renders the requested article in the current language of the user.
     *
     * @param webContext the current request to respond to
     * @param articleId  the id or code of the article to render
     */
    @Routed("/kba/:1")
    public void article(WebContext webContext, String articleId) {
        langArticle(webContext, NLS.getCurrentLang(), articleId);
    }

    /**
     * Renders the requested article in the given language.
     *
     * @param webContext the current request to respond to
     * @param lang       the language to render the article in
     * @param articleId  the id or code of the article to render
     */
    @Routed("/kba/:1/:2")
    public void langArticle(WebContext webContext, String lang, String articleId) {
        KnowledgeBaseArticle article = knowledgeBase.resolve(lang, articleId, false).orElse(null);
        if (article != null) {
            UserContext.getHelper(KBHelper.class).installCurrentArticle(article);
            webContext.respondWith().template(article.getTemplatePath());
        } else {
            webContext.respondWith().error(HttpResponseStatus.NOT_FOUND);
        }
    }

    /**
     * Renders the pre-authorized article in the given language.
     *
     * @param webContext the current request to respond to
     * @param lang       the language to render the article in
     * @param articleId  the id or code of the article to render
     * @param authKey    the authentication signature to verify
     */
    @Routed("/kba/:1/:2/:3")
    public void langArticle(WebContext webContext, String lang, String articleId, String authKey) {
        KnowledgeBaseArticle article = knowledgeBase.resolve(lang, articleId, true).orElse(null);
        if (article != null && (article.getEntry().checkPermissions()
                                || Strings.areEqual(article.computeAuthenticationSignature(true), authKey)
                                || Strings.areEqual(article.computeAuthenticationSignature(false), authKey))) {
            UserContext.getHelper(KBHelper.class).installCurrentArticle(article);
            webContext.respondWith().template(article.getTemplatePath());
        } else {
            webContext.respondWith().error(HttpResponseStatus.FORBIDDEN);
        }
    }
}
