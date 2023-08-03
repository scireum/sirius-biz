/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

import sirius.biz.process.Process;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.process.logs.ProcessLogAction;
import sirius.biz.process.logs.ProcessLogHandler;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.http.WebContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Provides a log message handler, which provides an action to directly jump to a {@link KnowledgeBaseArticle}.
 */
@Register(framework = KnowledgeBase.FRAMEWORK_KNOWLEDGE_BASE,
        classes = {OpenKbaLogHandler.class, ProcessLogHandler.class})
public class OpenKbaLogHandler implements ProcessLogHandler {

    /**
     * Defines the parameter which contains the ID of the article.
     */
    public static final String PARAM_ARTICLE_ID = "articleId";

    private static final String ACTION_OPEN_ARTICLE = "openKba";

    @Part
    private KnowledgeBase knowledgeBase;

    @Nullable
    @Override
    public String formatMessage(ProcessLog log) {
        return null;
    }

    @Override
    public List<ProcessLogAction> getActions(ProcessLog log) {
        KnowledgeBaseArticle article = resolveArticleId(log);
        if (article == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new ProcessLogAction(log, ACTION_OPEN_ARTICLE).withLabel(article.getTitle())
                                                                                       .withIcon("fa fa-lightbulb"));
    }

    @Override
    public boolean executeAction(WebContext request, Process process, ProcessLog log, String action, String returnUrl) {
        if (!ACTION_OPEN_ARTICLE.equals(action)) {
            return false;
        }
        KnowledgeBaseArticle article = resolveArticleId(log);
        if (article == null) {
            return false;
        }
        request.respondWith().redirectToGet("/kba/" + article.getLanguage() + "/" + article.getArticleId());
        return true;
    }

    @Nullable
    private KnowledgeBaseArticle resolveArticleId(ProcessLog log) {
        String articleId = log.getContext().get(PARAM_ARTICLE_ID).orElse(null);
        if (articleId == null) {
            return null;
        }
        KnowledgeBaseArticle article = knowledgeBase.resolve(NLS.getCurrentLanguage(), articleId, false).orElse(null);
        if (article == null) {
            return null;
        }
        return article;
    }

    @Nonnull
    @Override
    public String getName() {
        return "open-kba";
    }
}
