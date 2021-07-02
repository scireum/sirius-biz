/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.updates;

import sirius.db.mixing.Composite;

import java.time.LocalDateTime;

/**
 * Represents an update / news item loaded from a given feed by the {@link UpdateManager}.
 */
public class UpdateInfo extends Composite {

    private final String guid;
    private final LocalDateTime created;
    private final String label;
    private final String description;
    private final String link;
    private boolean important;

    /**
     * Creates a new update.
     *
     * @param guid        the unique id of this update
     * @param created     the timestamp when this update was created
     * @param label       the label or title
     * @param description a short description of the update
     * @param link        the URL pointing to the full article
     */
    public UpdateInfo(String guid, LocalDateTime created, String label, String description, String link) {
        this.guid = guid;
        this.created = created;
        this.label = label;
        this.description = description;
        this.link = link;
    }

    /**
     * Marks the update as noteworthy.
     *
     * @return the update itself for fluent method calls
     */
    public UpdateInfo markImportant() {
        this.important = true;
        return this;
    }

    public String getGuid() {
        return guid;
    }

    public LocalDateTime getCreated() {
        return created;
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

    public boolean isImportant() {
        return important;
    }
}
