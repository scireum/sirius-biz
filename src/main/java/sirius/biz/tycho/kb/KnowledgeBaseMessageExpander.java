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

import java.util.regex.Pattern;

/**
 * Expands blocks like <tt>kba:ABDCE</tt> or <tt>[... kba:ABDCE]</tt> into proper links to a {@link KnowledgeBaseEntry}.
 */
@Register
public class KnowledgeBaseMessageExpander implements MessageExpander {

    @Part
    private KnowledgeBase knowledgeBase;

    private static final Pattern LOCKED_KBA_PATTERN = Pattern.compile("\\[(.*?)kba:([a-zA-Z0-9]+)(.*)]");
    private static final Pattern KBA_PATTERN = Pattern.compile("kba:([a-zA-Z0-9]+)");

    @Override
    public String expand(String message) {
        message = LOCKED_KBA_PATTERN.matcher(message).replaceAll(match -> {
            return knowledgeBase.resolve(NLS.getCurrentLang(), match.group(2), false).map(kba -> {
                return match.group(1) + Strings.apply("""
                                                              <span class="d-inline-flex flex-row align-items-baseline">
                                                                  <i class="fa fa-lightbulb"></i><a class="pl-1" href="/kba/%s/%s">%s</a>
                                                              </span>
                                                              """,
                                                      kba.getLanguage(),
                                                      kba.getArticleId(),
                                                      kba.getTitle()) + match.group(3);
            }).orElse("");
        });
        return KBA_PATTERN.matcher(message).replaceAll(match -> {
            return knowledgeBase.resolve(NLS.getCurrentLang(), match.group(1), true).map(kba -> {
                return Strings.apply("""
                                             <span class="d-inline-flex flex-row align-items-baseline">
                                                 <i class="fa fa-lightbulb"></i><a class="pl-1" href="/kba/%s/%s">%s</a>
                                             </span>
                                             """, kba.getLanguage(), kba.getArticleId(), kba.getTitle());
            }).orElse("kba:" + match.group());
        });
    }
}
