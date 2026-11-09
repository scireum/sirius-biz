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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sirius.kernel.SiriusExtension;
import sirius.kernel.di.std.Part;
import sirius.pasta.tagliatelle.Tagliatelle;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @ParameterizedTest
    @ValueSource(strings = {"lang-text",
                            "lang-txt",
                            "lang-plain",
                            "lang-plaintext",
                            "lang-none",
                            "lang-Text",
                            "my-class lang-text"})
    public void plainTextIsExcludedFromHighlighting(String language) throws Exception {
        // Prettify skips any element marked "nocode"; without it, it guesses a language and colours prose as if it
        // were code, breaking off at the first non-ASCII letter.
        assertTrue(render(language).contains(" nocode\">"), render(language));
    }

    @ParameterizedTest
    @ValueSource(strings = {"lang-java", "", "lang-textile", "my-lang-text"})
    public void otherCodeIsLeftToPrettify(String language) throws Exception {
        // A named language is highlighted as such and a block without one is guessed, as before; a class which only
        // contains a plain-text name must not switch the highlighting off.
        assertFalse(render(language).contains("nocode"), render(language));
    }

    private String render(String language) throws Exception {
        return tagliatelle.resolve("/templates/kb/code.html.pasta").orElseThrow().renderToString(language);
    }

    @Part
    private static Tagliatelle tagliatelle;
}
