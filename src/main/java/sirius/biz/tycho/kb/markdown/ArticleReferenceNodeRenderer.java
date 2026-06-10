/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb.markdown;

import org.commonmark.node.Node;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlWriter;
import sirius.kernel.commons.StringCleanup;
import sirius.pasta.tagliatelle.TemplateReference;

import java.util.Set;

/**
 * Renders {@link ArticleReferenceNode} entries to proper KB links (or warning spans if unresolved).
 */
public class ArticleReferenceNodeRenderer implements NodeRenderer {

    private static final TemplateReference REF_TEMPLATE = new TemplateReference("/taglib/k/ref.html.pasta");

    private final HtmlWriter htmlWriter;

    /**
     * Creates a new ArticleReferenceNodeRenderer.
     *
     * @param context the context to use for rendering
     */
    public ArticleReferenceNodeRenderer(HtmlNodeRendererContext context) {
        this.htmlWriter = context.getWriter();
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return Set.of(ArticleReferenceNode.class);
    }

    @Override
    public void render(Node node) {
        ArticleReferenceNode referenceNode = (ArticleReferenceNode) node;
        htmlWriter.raw(REF_TEMPLATE.render(referenceNode.getArticleCode(),
                                           referenceNode.getAnchor().orElse(""),
                                           referenceNode.getCustomLabel().map(StringCleanup::escapeXml).orElse(""),
                                           "",
                                           ""));
    }
}
