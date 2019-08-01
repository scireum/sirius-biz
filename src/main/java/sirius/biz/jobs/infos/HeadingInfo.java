/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.infos;

/**
 * Renders a heading.
 */
public class HeadingInfo extends TextInfo {

    /**
     * Creates a new heading for the given text.
     *
     * @param text the text shown in the heading
     */
    public HeadingInfo(String text) {
        super(text);
    }

    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/infos/heading.html.pasta";
    }
}
