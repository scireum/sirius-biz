/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

import sirius.biz.analytics.explorer.EntityChartObjectResolver;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;

/**
 * Resolves {@link KnowledgeBaseEntry KB entries}.
 * <p>
 * Note that this doesn't perform any accessibility checks, as the referenced charts as well as the
 * used autocomplete already do.
 */
@Register(framework = KnowledgeBase.FRAMEWORK_KNOWLEDGE_BASE)
public class KnowledgeBaseEntryResolver extends EntityChartObjectResolver<KnowledgeBaseEntry> {

    @Override
    public Class<KnowledgeBaseEntry> getTargetType() {
        return KnowledgeBaseEntry.class;
    }

    @Override
    public String getAutocompleteUri() {
        return "/kb/autocomplete";
    }

    @Nonnull
    @Override
    public String getName() {
        return "KnowledgeBaseArticle";
    }
}
