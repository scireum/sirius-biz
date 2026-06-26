/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb.markdown;

import org.commonmark.ext.gfm.alerts.Alert;
import org.commonmark.ext.gfm.alerts.AlertTitle;
import org.commonmark.node.Node;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlWriter;
import sirius.kernel.commons.Value;
import sirius.kernel.nls.NLS;

import java.util.Map;
import java.util.Set;

/**
 * Renders GitHub-style alerts as Tycho-style alerts.
 */
public class AlertNodeRenderer implements NodeRenderer {

    private static final String ARGUMENT_CLASS = "class";
    private static final String TAG_DIV = "div";
    private final HtmlWriter htmlWriter;
    private final HtmlNodeRendererContext context;

    /**
     * Creates a new AlertNodeRenderer.
     *
     * @param context the context to use for rendering the alert
     */
    public AlertNodeRenderer(HtmlNodeRendererContext context) {
        this.htmlWriter = context.getWriter();
        this.context = context;
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return Set.of(Alert.class);
    }

    @Override
    public void render(Node node) {
        var alert = (Alert) node;

        AlertType alertType = Value.of(alert.getType()).upperCase().asEnum(AlertType.class);
        if (alertType == null) {
            throw new IllegalStateException("Unsupported alert type: " + alert.getType());
        }

        htmlWriter.line();

        htmlWriter.tag(TAG_DIV, Map.of(ARGUMENT_CLASS, "card mb-4 full-border " + alertType.sectionClass));
        htmlWriter.line();

        htmlWriter.tag(TAG_DIV, Map.of(ARGUMENT_CLASS, "card-body"));
        htmlWriter.line();

        // Render alert title
        htmlWriter.tag(TAG_DIV, Map.of(ARGUMENT_CLASS, "d-flex justify-content-between"));
        htmlWriter.line();
        htmlWriter.tag("h5",
                       Map.of(ARGUMENT_CLASS,
                              "card-title " + alertType.headingClass,
                              "style",
                              "scroll-margin-top: 100px;"));
        htmlWriter.tag("i", Map.of(ARGUMENT_CLASS, "fa-solid " + alertType.iconClass));
        htmlWriter.tag("/i");
        renderTitle(alert, alertType);
        htmlWriter.tag("/h5");
        htmlWriter.tag("/div");
        htmlWriter.line();

        // Render children (the alert content)
        renderChildNodes(alert);

        htmlWriter.tag("/div");
        htmlWriter.line();

        htmlWriter.tag("/div");
        htmlWriter.line();
    }

    /**
     * Renders the alert title, preferring an author-provided custom title (which may contain inline formatting) over
     * the localized default heading for the alert type.
     */
    private void renderTitle(Alert alert, AlertType alertType) {
        if (alert.getFirstChild() instanceof AlertTitle alertTitle) {
            for (Node child = alertTitle.getFirstChild(); child != null; child = child.getNext()) {
                context.render(child);
            }
        } else {
            htmlWriter.text(alertType.gethHeading());
        }
    }

    private void renderChildNodes(Alert alert) {
        var node = alert.getFirstChild();
        while (node != null) {
            var next = node.getNext();
            // The custom title (if any) lives in an AlertTitle node and is already rendered as the heading.
            if (!(node instanceof AlertTitle)) {
                context.render(node);
            }
            node = next;
        }
    }

    private enum AlertType {
        NOTE("border-sirius-blue-light", "text-sirius-blue-light", "fa-info-circle"),
        TIP("border-sirius-green-light", "text-sirius-green-light", "fa-lightbulb"),
        WARNING("border-sirius-yellow-dark", "text-sirius-yellow-dark", "fa-exclamation-triangle");

        private final String sectionClass;
        private final String headingClass;
        private final String iconClass;

        AlertType(String sectionClass, String headingClass, String iconClass) {
            this.sectionClass = sectionClass;
            this.headingClass = headingClass;
            this.iconClass = iconClass;
        }

        private String gethHeading() {
            return NLS.get("AlertNodeRenderer.type." + name());
        }
    }
}
