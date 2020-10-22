/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.HelperFactory;
import sirius.web.security.ScopeInfo;

import javax.annotation.Nonnull;
import java.util.concurrent.ThreadLocalRandom;

public class KBHelper {

    @Register
    public static class KBHelperFactory implements HelperFactory {

        @Nonnull
        @Override
        public Class getHelperType() {
            return KBHelper.class;
        }

        @Nonnull
        @Override
        public String getName() {
            return "tycho-kb";
        }

        @Nonnull
        @Override
        public Object make(@Nonnull ScopeInfo scope) {
            return new KBHelper();
        }
    }

    @Part
    private static KnowledgeBase knowledgeBase;

    private ThreadLocal<KnowledgeBaseArticle> currentArticle = new ThreadLocal<>();

    public KnowledgeBaseArticle currentArticle() {
        return currentArticle.get();
    }

    public void installCurrentArticle(KnowledgeBaseArticle article) {
        currentArticle.set(article);
    }
}
