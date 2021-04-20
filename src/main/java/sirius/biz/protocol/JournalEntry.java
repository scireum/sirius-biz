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
import sirius.kernel.async.TaskContext;
import sirius.kernel.di.std.Framework;

import java.time.LocalDateTime;

/**
 * Records the changes recorded by a {@link JournalData}.
 */
@Framework(Protocols.FRAMEWORK_JOURNAL)
public class JournalEntry extends SearchableEntity {

    /**
     * Contains the timestamp of the change.
     */
    public static final Mapping TOD = Mapping.named("tod");
    private LocalDateTime tod;

    /**
     * Contains the name of the user which was active when the change occured.
     */
    public static final Mapping USERNAME = Mapping.named("username");
    @SearchContent
    private String username;

    /**
     * Contaisn the id of the user which was active when the change occured.
     */
    public static final Mapping USER_ID = Mapping.named("userId");
    private String userId;

    /**
     * Contains the system string, which indicates where the change occured.
     *
     * @see TaskContext#getSystemString()
     */
    public static final Mapping SUBSYSTEM = Mapping.named("subsystem");
    @SearchContent
    private String subsystem;

    /**
     * Contains the type name of the entity which owns this change record.
     */
    public static final Mapping TARGET_TYPE = Mapping.named("targetType");
    @SearchContent
    private String targetType;

    /**
     * Contains the ID of entity which owns this change record.
     */
    public static final Mapping TARGET_ID = Mapping.named("targetId");
    @SearchContent
    private String targetId;

    /**
     * Contains the {@code toString()} of the entity which was changed.
     */
    public static final Mapping TARGET_NAME = Mapping.named("targetName");
    @SearchContent
    private String targetName;

    /**
     * Contains all changed fields as <tt>name: value</tt>.
     * <p>
     * The old values are not recorded, as these are in the previous protocol entry.
     */
    public static final Mapping CHANGES = Mapping.named("changes");
    @SearchContent
    @IndexMode(indexed = ESOption.FALSE, docValues = ESOption.FALSE)
    private String changes;

    public LocalDateTime getTod() {
        return tod;
    }

    public void setTod(LocalDateTime tod) {
        this.tod = tod;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSubsystem() {
        return subsystem;
    }

    public void setSubsystem(String subsystem) {
        this.subsystem = subsystem;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public String getChanges() {
        return changes;
    }

    public void setChanges(String changes) {
        this.changes = changes;
    }
}
