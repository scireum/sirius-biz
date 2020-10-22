/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.search;

public class OpenSearchResult {
    private String label;
    private String description;
    private String url;

    public OpenSearchResult(String label, String description, String url) {
        this.label = label;
        this.description = description;
        this.url = url;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public String getUrl() {
        return url;
    }
}
