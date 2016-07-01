/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.biz.protocol.NoJournal;
import sirius.db.mixing.Column;
import sirius.db.mixing.Composite;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.kernel.async.TaskContext;
import sirius.web.security.UserContext;

import java.time.LocalDateTime;

/**
 * Provides tracing information which can be embedded into other entities or mixins.
 */
public class TraceData extends Composite {

    /**
     * Stores the username of the user which created the assoicated entity.
     */
    public static final Column CREATED_BY = Column.named("createdBy");
    @NoJournal
    @NullAllowed
    @Length(length = 50)
    private String createdBy;

    /**
     * Stores the timstamp when the associated entity was created.
     */
    public static final Column CREATED_AT = Column.named("createdAt");
    @NoJournal
    @NullAllowed
    private LocalDateTime createdAt;

    /**
     * Stores the system string ({@link TaskContext#getSystemString()} where the associated entity was created.
     */
    public static final Column CREATED_IN = Column.named("createdIn");
    @NoJournal
    @NullAllowed
    @Length(length = 150)
    private String createdIn;

    /**
     * Stores the username of the user which last changed the associated entity.
     */
    public static final Column CHANGED_BY = Column.named("changedBy");
    @NoJournal
    @NullAllowed
    @Length(length = 50)
    private String changedBy;

    /**
     * Stores the timestamp when the associated entity was last changed.
     */
    public static final Column CHANGED_AT = Column.named("changedAt");
    @NoJournal
    @NullAllowed
    private LocalDateTime changedAt;

    /**
     * Stores the system string ({@link TaskContext#getSystemString()} where the associated entity was last changed.
     */
    public static final Column CHANGED_IN = Column.named("changedIn");
    @NoJournal
    @NullAllowed
    @Length(length = 150)
    private String changedIn;

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

    /**
     * Returns the username which created the associated entity.
     *
     * @return the username of the user which created the associated entity
     */
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * Returns the timestamp when the entity was created.
     *
     * @return the timestamp when the entity was created
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns the system string, where the entity was created.
     *
     * @return the value of {@link TaskContext#getSystemString()} when the entity was created
     */
    public String getCreatedIn() {
        return createdIn;
    }

    /**
     * Returns the username which last changed the associated entity.
     *
     * @return the username of the user which last changed the associated entity
     */
    public String getChangedBy() {
        return changedBy;
    }

    /**
     * Returns the timestamp when the entity was last changed.
     *
     * @return the timestamp when the entity was last changed
     */
    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    /**
     * Returns the system string, where the entity was last changed.
     *
     * @return the value of {@link TaskContext#getSystemString()} when the entity was last changed
     */
    public String getChangedIn() {
        return changedIn;
    }

    /**
     * Determines if change tracking is currently disabled.
     *
     * @return <tt>true</tt>, if change tracking is currently disabled, <tt>false</tt> otherwise
     */
    public boolean isSilent() {
        return silent;
    }

    /**
     * Can be used to disable or re-enable change tracking.
     * <p>
     * Some changes, performed by the system, should not update the tracing information. A login of a user
     * (which might increment its login counter) would be an example, where change tracking should be disabled.
     *
     * @param silent <tt>true</tt> if change tracking for the current entity instance should be disabled or
     *               <tt>false</tt> to re-enable
     */
    public void setSilent(boolean silent) {
        this.silent = silent;
    }
}
