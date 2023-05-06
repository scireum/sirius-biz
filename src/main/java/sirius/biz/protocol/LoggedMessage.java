/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import sirius.biz.elastic.SearchContent;
import sirius.biz.elastic.SearchableEntity;
import sirius.db.es.annotations.ESOption;
import sirius.db.es.annotations.IndexMode;
import sirius.db.mixing.Mapping;
import sirius.kernel.di.std.Framework;
import sirius.kernel.health.Log;

import java.time.LocalDateTime;

/**
 * Stores a log message created via a {@link Log} instance.
 */
@Framework(Protocols.FRAMEWORK_PROTOCOLS)
public class LoggedMessage extends SearchableEntity {

    /**
     * Contains the message itself.
     */
    public static final Mapping MESSAGE = Mapping.named("message");
    @SearchContent
    @IndexMode(indexed = ESOption.FALSE, docValues = ESOption.FALSE)
    private String message;

    /**
     * Contains the category (name of the logger) which created the message.
     */
    public static final Mapping CATEGORY = Mapping.named("category");
    @SearchContent
    private String category;

    /**
     * Contains the level the message was logged at.
     */
    public static final Mapping LEVEL = Mapping.named("level");
    private String level;

    /**
     * Contains the node the message was logged on.
     */
    public static final Mapping NODE = Mapping.named("node");
    @SearchContent
    private String node;

    /**
     * Contains the timestamp when the message was logged.
     */
    public static final Mapping TOD = Mapping.named("tod");
    private LocalDateTime tod = LocalDateTime.now();

    /**
     * Contains the user that was logged in, when the message was logged.
     */
    public static final Mapping USER = Mapping.named("user");
    @SearchContent
    private String user;

    /**
     * Computes an appropriate CSS class used to render the message.
     *
     * @return the name of a CSS class based on the level of the message.
     */
    public String getRowClass() {
        if ("ERROR".equals(getLevel())) {
            return "sci-left-border-red";
        }
        if ("WARN".equals(getLevel())) {
            return "sci-left-border-yellow";
        }
        if ("DEBUG".equals(getLevel())) {
            return "sci-left-border-gray";
        }
        return "sci-left-border-blue";
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public LocalDateTime getTod() {
        return tod;
    }

    public void setTod(LocalDateTime tod) {
        this.tod = tod;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }
}
