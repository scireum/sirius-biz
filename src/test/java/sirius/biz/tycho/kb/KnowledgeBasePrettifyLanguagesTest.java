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
import sirius.kernel.Sirius;
import sirius.kernel.SiriusExtension;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that the knowledge base page knows exactly the Prettify language files which are shipped.
 * <p>
 * The page loads a language file only if the language is listed, so a file missing from the list is never used, and
 * a listed file which is not shipped costs every page naming that language a failing request.
 */
@ExtendWith(SiriusExtension.class)
public class KnowledgeBasePrettifyLanguagesTest {

    @Test
    public void listedLanguagesMatchTheShippedLanguageFiles() throws Exception {
        Set<String> shipped = Sirius.getClasspath()
                                    .find(Pattern.compile("assets/libs/prettify/lang-([A-Za-z0-9]+)\\.js"))
                                    .map(match -> match.group(1))
                                    .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(shipped, listedLanguages());
    }

    private Set<String> listedLanguages() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/default/taglib/k/base.html.pasta")) {
            String template = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            Matcher list = Pattern.compile("PRETTIFY_LANGUAGE_FILES = new Set\\(\\[([^]]*)]\\)").matcher(template);
            if (!list.find()) {
                throw new IllegalStateException("The list of Prettify language files is missing from k:base.");
            }
            return Pattern.compile("'([^']+)'")
                          .matcher(list.group(1))
                          .results()
                          .map(match -> match.group(1))
                          .collect(Collectors.toCollection(TreeSet::new));
        }
    }
}
