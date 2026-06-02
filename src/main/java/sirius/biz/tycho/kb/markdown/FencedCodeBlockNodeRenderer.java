/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb.markdown;

import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlWriter;
import sirius.kernel.commons.StringCleanup;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.pasta.tagliatelle.TemplateReference;

import java.util.Set;

/**
 * Renders fenced code blocks through the Tycho KB Tagliatelle tags.
 */
public class FencedCodeBlockNodeRenderer implements NodeRenderer {

    private static final TemplateReference CODE_TEMPLATE = new TemplateReference("/taglib/k/code.html.pasta");
    private static final TemplateReference MERMAID_TEMPLATE = new TemplateReference("/taglib/k/mermaid.html.pasta");

    private final HtmlWriter htmlWriter;

    /**
     * Creates a new FencedCodeBlockNodeRenderer.
     *
     * @param context the context to use for rendering
     */
    public FencedCodeBlockNodeRenderer(HtmlNodeRendererContext context) {
        this.htmlWriter = context.getWriter();
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return Set.of(FencedCodeBlock.class);
    }

    @Override
    public void render(Node node) {
        FencedCodeBlock codeBlock = (FencedCodeBlock) node;
        String info = Value.of(codeBlock.getInfo()).trim();
        String language = Strings.isFilled(info) ? info.split("\\s+", 2)[0] : "";

        htmlWriter.line();
        if (Strings.areEqual(language, "mermaid")) {
            htmlWriter.raw(MERMAID_TEMPLATE.render("", codeBlock.getLiteral()));
        } else {
            htmlWriter.raw(CODE_TEMPLATE.render(createLanguageClass(language),
                                                StringCleanup.escapeXml(codeBlock.getLiteral())));
        }
        htmlWriter.line();
    }

    private String createLanguageClass(String language) {
        if (Strings.isEmpty(language)) {
            return "";
        }

        return "lang-" + language.replaceAll("[^a-zA-Z0-9_-]", "-");
    }
}
