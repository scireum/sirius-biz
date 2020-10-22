/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

import sirius.biz.web.BizController;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Controller;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.security.UserContext;

@Register(classes = Controller.class, framework = KnowledgeBase.FRAMEWORK_KNOWLEDGE_BASE)
public class KnowledgeBaseController extends BizController {

    @Part
    private KnowledgeBase knowledgeBase;

    @Routed("/tycho/kb")
    @LoginRequired
    public void kb(WebContext webContext) {
        article(webContext, KnowledgeBase.ROOT_CHAPTER_ID);
    }

    @Routed("/tycho/kb/:1")
    @LoginRequired
    public void langKb(WebContext webContext, String lang) {
        langArticle(webContext, lang, KnowledgeBase.ROOT_CHAPTER_ID);
    }

    @Routed("/tycho/kba/:1")
    @LoginRequired
    public void article(WebContext webContext, String articleId) {
        KnowledgeBaseArticle article = knowledgeBase.resolve("de", articleId).get();
        UserContext.getHelper(KBHelper.class).installCurrentArticle(article);
        webContext.respondWith().template(article.getTemplatePath());
    }

    @Routed("/tycho/kba/:1/:2")
    @LoginRequired
    public void langArticle(WebContext webContext, String lang, String articleId) {
        KnowledgeBaseArticle article = knowledgeBase.resolve(lang, articleId).get();
        UserContext.getHelper(KBHelper.class).installCurrentArticle(article);
        webContext.respondWith().template(article.getTemplatePath());
    }
}
