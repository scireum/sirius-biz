/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.controller.MessageExpander;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Expands blocks like <tt>kba:ABDCE#section-anchor</tt> or <tt>[... kba:ABDCE#section-anchor ...]</tt> into proper links to a {@link KnowledgeBaseEntry}.
 */
@Register
public class KnowledgeBaseMessageExpander implements MessageExpander {

    private static final Pattern LOCKED_KBA_PATTERN =
            Pattern.compile("\\[(.*?)kba:([a-zA-Z0-9]+)(#[a-zA-Z0-9-_]+)?(.*)]");
    private static final Pattern KBA_PATTERN = Pattern.compile("kba:([a-zA-Z0-9]+)(#[a-zA-Z0-9-_]+)?");

    private static final String EXPANDED_LINK_TEMPLATE = """
            <span class="d-inline-flex flex-row align-items-baseline">
                <i class="fa-solid fa-lightbulb"></i><a class="ps-1" href="/kba/%s/%s%s">%s</a>
            </span>
            """;

    @Part
    private KnowledgeBase knowledgeBase;

    @Override
    public String expand(String message) {
        message = LOCKED_KBA_PATTERN.matcher(message).replaceAll(match -> {
            return knowledgeBase.resolve(NLS.getCurrentLanguage(), match.group(2), false).map(kba -> {
                return match.group(1) + Strings.apply(EXPANDED_LINK_TEMPLATE,
                                                      kba.getLanguage(),
                                                      kba.getArticleId(),
                                                      Optional.ofNullable(match.group(3)).orElse(""),
                                                      kba.getTitle()) + match.group(4);
            }).orElse("");
        });
        return KBA_PATTERN.matcher(message).replaceAll(match -> {
            return knowledgeBase.resolve(NLS.getCurrentLanguage(), match.group(1), true).map(kba -> {
                return Strings.apply(EXPANDED_LINK_TEMPLATE,
                                     kba.getLanguage(),
                                     kba.getArticleId(),
                                     Optional.ofNullable(match.group(2)).orElse(""),
                                     kba.getTitle());
            }).orElse("kba:" + match.group());
        });
    }
}
