/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb.markdown;

import org.commonmark.node.Code;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlWriter;
import sirius.kernel.commons.StringCleanup;
import sirius.pasta.tagliatelle.TemplateReference;

import java.util.Set;

/**
 * Renders Markdown links through the Tycho KB Tagliatelle link tag.
 */
public class LinkNodeRenderer implements NodeRenderer {

    private static final TemplateReference LINK_TEMPLATE = new TemplateReference("/taglib/k/link.html.pasta");

    private final HtmlNodeRendererContext context;
    private final HtmlWriter htmlWriter;

    /**
     * Creates a new LinkNodeRenderer.
     *
     * @param context the context to use for rendering
     */
    public LinkNodeRenderer(HtmlNodeRendererContext context) {
        this.context = context;
        this.htmlWriter = context.getWriter();
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return Set.of(Link.class);
    }

    @Override
    public void render(Node node) {
        Link link = (Link) node;
        htmlWriter.raw(LINK_TEMPLATE.render("",
                                            context.encodeUrl(link.getDestination()),
                                            "",
                                            StringCleanup.escapeXml(extractLabel(link))));
    }

    private String extractLabel(Link link) {
        StringBuilder label = new StringBuilder();
        appendTextContent(link.getFirstChild(), label);
        return label.toString().strip();
    }

    private void appendTextContent(Node node, StringBuilder text) {
        while (node != null) {
            if (node instanceof Text textNode) {
                text.append(textNode.getLiteral());
            } else if (node instanceof Code codeNode) {
                text.append(codeNode.getLiteral());
            } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
                text.append(" ");
            }

            appendTextContent(node.getFirstChild(), text);
            node = node.getNext();
        }
    }
}
