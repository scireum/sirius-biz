/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import sirius.kernel.SiriusExtension;
import sirius.web.sass.Generator;
import sirius.web.sass.Output;
import sirius.web.sass.Parser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the trailing-margin rules of the knowledge base stylesheet.
 * <p>
 * A broken stylesheet is already caught by {@code ReportBrokenStylesheets}, but only as a parse error. What this
 * covers is the shape of the generated selectors, which no parse check can see and which the rules depend on for
 * their effect.
 */
@ExtendWith(SiriusExtension.class)
public class KnowledgeBaseStylesheetTest {

    @Test
    public void trailingMarginIsResetAlongTheWholeLastChildChain() {
        // A container without bottom padding collapses its last child's margin outwards, so resetting only the
        // outermost element leaves the gap in place for a list of paragraphs or a list nested inside one.
        assertTrue(css.contains(".kb-section > .card-body > *:last-child {"), css);
        assertTrue(css.contains(".kb-section > .card-body > *:last-child > *:last-child > *:last-child {"), css);
    }

    @Test
    public void nestedSectionOverridesTheImportantSpacingUtility() {
        // A hint box closing a section carries Bootstrap's "mb-4", which is declared "!important" and therefore
        // survives a plain reset.
        assertTrue(css.replaceAll("\\s+", " ")
                      .contains(".kb-section > .card-body > .kb-section:last-child { margin-bottom: 0 !important;"),
                   css);
    }

    @BeforeAll
    static void compileStylesheet() throws Exception {
        Generator generator = new Generator();
        try (InputStream stream = KnowledgeBaseStylesheetTest.class.getResourceAsStream(STYLESHEET)) {
            generator.importStylesheet(new Parser(STYLESHEET, new InputStreamReader(stream)).parse());
        }
        generator.compile();
        StringWriter writer = new StringWriter();
        generator.generate(new Output(writer, false));
        css = writer.toString();
    }

    private static final String STYLESHEET = "/default/assets/styles/kb.scss";

    private static String css;
}
