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
import org.commonmark.node.Image;
import org.commonmark.node.ListBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlWriter;
import sirius.pasta.tagliatelle.TemplateReference;

import java.util.Set;

/**
 * Renders standalone Markdown images as Tycho preview images.
 */
public class KbaPreviewImageNodeRenderer implements org.commonmark.renderer.NodeRenderer {

    private static final TemplateReference PREVIEW_IMAGE_TEMPLATE =
            new TemplateReference("/taglib/k/previewImage.html.pasta");

    private final HtmlNodeRendererContext context;
    private final HtmlWriter htmlWriter;

    /**
     * Creates a new KbaPreviewImageNodeRenderer.
     *
     * @param context the context to use for rendering
     */
    public KbaPreviewImageNodeRenderer(HtmlNodeRendererContext context) {
        this.context = context;
        this.htmlWriter = context.getWriter();
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return Set.of(Paragraph.class);
    }

    @Override
    public void render(Node node) {
        if (isStandaloneImageParagraph(node)) {
            renderPreviewImage((Image) node.getFirstChild());
            return;
        }

        if (isInsideTightListItem(node)) {
            renderChildren(node);
            return;
        }

        htmlWriter.line();
        htmlWriter.tag("p");
        renderChildren(node);
        htmlWriter.tag("/p");
        htmlWriter.line();
    }

    private boolean isStandaloneImageParagraph(Node node) {
        return node instanceof Paragraph
               && node.getFirstChild() instanceof Image
               && node.getFirstChild().getNext() == null;
    }

    private boolean isInsideTightListItem(Node node) {
        return node.getParent() instanceof ListItem
               && node.getParent().getParent() instanceof ListBlock listBlock
               && listBlock.isTight();
    }

    private void renderPreviewImage(Image image) {
        htmlWriter.line();
        htmlWriter.raw(PREVIEW_IMAGE_TEMPLATE.render(context.encodeUrl(image.getDestination()),
                                                     extractAltText(image).strip(),
                                                     "",
                                                     ""));
        htmlWriter.line();
    }

    private String extractAltText(Image image) {
        StringBuilder altText = new StringBuilder();
        appendTextContent(image.getFirstChild(), altText);
        return altText.toString();
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

    private void renderChildren(Node parent) {
        Node node = parent.getFirstChild();
        while (node != null) {
            Node next = node.getNext();
            context.render(node);
            node = next;
        }
    }
}
