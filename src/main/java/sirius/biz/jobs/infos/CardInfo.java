/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.infos;

/**
 * Renders a text block wrapped in a card.
 */
public class CardInfo extends TextInfo {

    /**
     * Creates a new text block.
     *
     * @param text the text to output in a well
     */
    public CardInfo(String text) {
        super(text);
    }

    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/infos/card.html.pasta";
    }
}
