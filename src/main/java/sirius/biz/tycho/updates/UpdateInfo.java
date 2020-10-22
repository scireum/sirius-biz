/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.updates;

import java.time.LocalDateTime;

public class UpdateInfo {

    private LocalDateTime timestamp;
    private String label;
    private String description;
    private String link;

    public UpdateInfo(LocalDateTime timestamp, String label, String description, String link) {
        this.timestamp = timestamp;
        this.label = label;
        this.description = description;
        this.link = link;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public String getLink() {
        return link;
    }
}
