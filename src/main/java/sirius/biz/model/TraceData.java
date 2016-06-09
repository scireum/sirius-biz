/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.biz.protocol.NoJournal;
import sirius.kernel.async.TaskContext;
import sirius.db.mixing.Column;
import sirius.db.mixing.Composite;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.web.security.UserContext;

import java.time.LocalDateTime;

/**
 * Created by aha on 07.05.15.
 */
public class TraceData extends Composite {

    @NoJournal
    @NullAllowed
    @Length(length = 50)
    private String createdBy;
    public static final Column CREATED_BY = Column.named("createdBy");

    @NoJournal
    @NullAllowed
    private LocalDateTime createdAt;
    public static final Column CREATED_AT = Column.named("createdAt");

    @NoJournal
    @NullAllowed
    @Length(length = 150)
    private String createdIn;
    public static final Column CREATED_IN = Column.named("createdIn");

    @NoJournal
    @NullAllowed
    @Length(length = 50)
    private String changedBy;
    public static final Column CHANGED_BY = Column.named("changedBy");

    @NoJournal
    @NullAllowed
    private LocalDateTime changedAt;
    public static final Column CHANGED_AT = Column.named("changedAt");

    @NoJournal
    @NullAllowed
    @Length(length = 150)
    private String changedIn;
    public static final Column CHANGED_IN = Column.named("changedIn");

    @Transient
    private boolean silent;

    @BeforeSave
    protected void update() {
        if (createdAt == null) {
            createdBy = UserContext.getCurrentUser().getUserName();
            createdAt = LocalDateTime.now();
            createdIn = TaskContext.get().getSystemString();
        }
        if (!silent) {
            changedBy = UserContext.getCurrentUser().getUserName();
            changedAt = LocalDateTime.now();
            changedIn = TaskContext.get().getSystemString();
        }
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedIn() {
        return createdIn;
    }

    public void setCreatedIn(String createdIn) {
        this.createdIn = createdIn;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }

    public String getChangedIn() {
        return changedIn;
    }

    public void setChangedIn(String changedIn) {
        this.changedIn = changedIn;
    }

    public boolean isSilent() {
        return silent;
    }

    public void setSilent(boolean silent) {
        this.silent = silent;
    }
}
