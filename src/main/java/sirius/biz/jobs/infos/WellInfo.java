/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.infos;

/**
 * Renders a text block wrapped in a well.
 */
public class WellInfo extends TextInfo {

    /**
     * Creates a new text block.
     *
     * @param text the text to output in a well
     */
    public WellInfo(String text) {
        super(text);
    }

    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/infos/well.html.pasta";
    }
}
