/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.infos;

/**
 * Renders a block of unescaped HTML.
 */
public class HTMLInfo extends TextInfo {

    /**
     * Creates a new block of HTML.
     *
     * @param text the html to output
     */
    public HTMLInfo(String text) {
        super(text);
    }

    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/infos/html.html.pasta";
    }
}
