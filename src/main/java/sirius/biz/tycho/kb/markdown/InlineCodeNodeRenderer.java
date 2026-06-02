/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb.markdown;

import org.commonmark.node.Code;
import org.commonmark.node.Node;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlWriter;
import sirius.kernel.commons.StringCleanup;
import sirius.pasta.tagliatelle.TemplateReference;

import java.util.Set;

/**
 * Renders inline code through the Tycho KB Tagliatelle tag.
 */
public class InlineCodeNodeRenderer implements NodeRenderer {

    private static final TemplateReference INLINE_CODE_TEMPLATE =
            new TemplateReference("/taglib/k/inlineCode.html.pasta");

    private final HtmlWriter htmlWriter;

    /**
     * Creates a new InlineCodeNodeRenderer.
     *
     * @param context the context to use for rendering
     */
    public InlineCodeNodeRenderer(HtmlNodeRendererContext context) {
        this.htmlWriter = context.getWriter();
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return Set.of(Code.class);
    }

    @Override
    public void render(Node node) {
        Code code = (Code) node;
        htmlWriter.raw(INLINE_CODE_TEMPLATE.render(StringCleanup.escapeXml(code.getLiteral())));
    }
}
