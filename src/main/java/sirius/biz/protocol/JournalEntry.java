/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import sirius.db.mixing.Mapping;
import sirius.db.jdbc.SQLEntity;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.Lob;
import sirius.kernel.async.TaskContext;
import sirius.kernel.di.std.Framework;

import java.time.LocalDateTime;

/**
 * Records the changes recorded by a {@link JournalData}.
 */
@Framework(Protocols.FRAMEWORK_JOURNAL)
public class JournalEntry extends SQLEntity {

    /**
     * Contains the timestamp of the change.
     */
    public static final Mapping TOD = Mapping.named("tod");
    private LocalDateTime tod;

    /**
     * Contains the name of the user which was active when the change occured.
     */
    public static final Mapping USERNAME = Mapping.named("username");
    @Length(255)
    private String username;

    /**
     * Contaisn the id of the user which was active when the change occured.
     */
    public static final Mapping USER_ID = Mapping.named("userId");
    @Length(255)
    private String userId;

    /**
     * Contains the system string, which indicates where the change occured.
     *
     * @see TaskContext#getSystemString()
     */
    public static final Mapping SUBSYSTEM = Mapping.named("subsystem");
    @Length(255)
    private String subsystem;

    /**
     * Contains the type name of the entity which was changed.
     */
    public static final Mapping TARGET_TYPE = Mapping.named("targetType");
    @Length(255)
    private String targetType;

    /**
     * Contains the ID of entity which was changed.
     */
    public static final Mapping TARGET_ID = Mapping.named("targetId");
    private long targetId;

    /**
     * Contains the {@code toString()} of the entity which was changed.
     */
    public static final Mapping TARGET_NAME = Mapping.named("targetName");
    @Length(255)
    private String targetName;

    /**
     * Contains all changed fields as <tt>name: value</tt>.
     * <p>
     * The old values are not recorded, as these are in the previous protocol entry.
     */
    public static final Mapping CHANGES = Mapping.named("changes");
    @Lob
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

    public long getTargetId() {
        return targetId;
    }

    public void setTargetId(long targetId) {
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
