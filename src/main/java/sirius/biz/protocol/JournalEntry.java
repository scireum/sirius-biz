/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import sirius.db.mixing.Column;
import sirius.db.mixing.Entity;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.Lob;
import sirius.kernel.di.std.Framework;

import java.time.LocalDateTime;

/**
 * Created by aha on 18.02.16.
 */
@Framework(Protocols.FRAMEWORK_PROTOCOLS)
public class JournalEntry extends Entity {

    private LocalDateTime tod;
    public static final Column TOD = Column.named("tod");

    @Length(length = 255)
    private String username;
    public static final Column USERNAME = Column.named("username");

    @Length(length = 255)
    private String userId;
    public static final Column USER_ID = Column.named("userId");

    @Length(length = 255)
    private String subsystem;
    public static final Column SUBSYSTEM = Column.named("subsystem");

    @Length(length = 255)
    private String targetType;
    public static final Column TARGET_TYPE = Column.named("targetType");

    private long targetId;
    public static final Column TARGET_ID = Column.named("targetId");

    @Length(length = 255)
    private String targetName;
    public static final Column TARGET_NAME = Column.named("targetName");

    @Lob
    private String changes;
    public static final Column CHANGES = Column.named("changes");

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
