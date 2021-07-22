/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.infos;

/**
 * Renders a text block.
 */
public class TextInfo implements JobInfo {

    private final String text;

    /**
     * Creates a new text block.
     *
     * @param text the text to show
     */
    public TextInfo(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/infos/text.html.pasta";
    }
}
