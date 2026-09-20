/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import sirius.kernel.SiriusExtension;
import sirius.kernel.di.std.Part;
import sirius.pasta.tagliatelle.Tagliatelle;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the markup contract of the {@code k:section} tag which the knowledge base stylesheet relies on.
 */
@ExtendWith(SiriusExtension.class)
public class KnowledgeBaseSectionTagTest {

    @Test
    public void sectionCarriesTheKnowledgeBaseClass() throws Exception {
        String html = render();

        // "kb-section" is what scopes kb.scss to knowledge base cards; without it the stylesheet would either miss
        // them or have to match ".card", which is every card in every Tycho application.
        assertTrue(html.contains("kb-section"), html);
    }

    @Test
    public void sectionKeepsItsBodyAsADirectChildOfTheCardBody() throws Exception {
        String html = render();

        // The stylesheet addresses the trailing element with a direct-child selector, so an additional wrapper
        // around the body would silently stop the rules from matching.
        assertTrue(html.replaceAll("\\s+", "").contains("</div><p>Body</p></div>"), html);
    }

    private String render() throws Exception {
        return tagliatelle.resolve("/templates/kb/section.html.pasta").orElseThrow().renderToString();
    }

    @Part
    private static Tagliatelle tagliatelle;
}
