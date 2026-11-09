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
 * Verifies the markup contract of the {@code k:code} tag which the knowledge base stylesheet and Prettify rely on.
 */
@ExtendWith(SiriusExtension.class)
public class KnowledgeBaseCodeTagTest {

    @Test
    public void codeBlockCarriesTheKnowledgeBaseClass() throws Exception {
        // "kb-code" is what gives the block its text colour in kb.scss; without it, text Prettify leaves alone keeps
        // the page's dark colour on the dark background.
        assertTrue(render("lang-java").contains("<pre class=\"kb-code prettyprint p-2 lang-java\">"),
                   render("lang-java"));
    }

    private String render(String language) throws Exception {
        return tagliatelle.resolve("/templates/kb/code.html.pasta").orElseThrow().renderToString(language);
    }

    @Part
    private static Tagliatelle tagliatelle;
}
