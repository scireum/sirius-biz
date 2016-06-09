/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import sirius.db.mixing.Entity;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.Lob;

import java.time.LocalDateTime;

/**
 * Created by aha on 18.02.16.
 */
public class LogEntry extends Entity {

    @Lob
    private String message;
    public static final String MESSAGE = "message";

    @Length(length = 255)
    private String category;
    public static final String CATEGORY = "category";

    @Length(length = 255)
    private String level;
    public static final String LEVEL = "level";

    @Length(length = 255)
    private String node;
    public static final String NODE = "node";

    private LocalDateTime tod = LocalDateTime.now();
    public static final String TOD = "tod";

    @Length(length = 255)
    private String user;
    public static final String USER = "user";

    public String getLabelClass() {
        if ("ERROR".equals(getLevel())) {
            return "label-important";
        }
        if ("WARN".equals(getLevel())) {
            return "label-warning";
        }
        if ("DEBUG".equals(getLevel())) {
            return "";
        }
        return "label-info";
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
