/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import sirius.biz.importer.IgnoreInImportChangedCheck;
import sirius.db.mixing.Composite;
import sirius.db.mixing.DateRange;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.kernel.async.CallContext;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Strings;
import sirius.web.security.UserContext;

import java.time.LocalDateTime;

/**
 * Provides tracing information which can be embedded into other entities or mixins.
 */
public class TraceData extends Composite {

    /**
     * Stores the username of the user which created the associated entity.
     */
    public static final Mapping CREATED_BY = Mapping.named("createdBy");
    @NoJournal
    @NullAllowed
    @Length(50)
    private String createdBy;

    /**
     * Stores the timestamp when the associated entity was created.
     */
    public static final Mapping CREATED_AT = Mapping.named("createdAt");
    @NoJournal
    @NullAllowed
    private LocalDateTime createdAt;

    /**
     * Stores the system string ({@link TaskContext#getSystemString()}) where the associated entity was created.
     */
    public static final Mapping CREATED_IN = Mapping.named("createdIn");
    @NoJournal
    @NullAllowed
    @Length(150)
    private String createdIn;

    /**
     * Stores the node string ({@link CallContext#getNodeName()}) where the associated entity was created.
     */
    public static final Mapping CREATED_ON = Mapping.named("createdOn");
    @NoJournal
    @NullAllowed
    @Length(50)
    private String createdOn;

    /**
     * Stores the username of the user which last changed the associated entity.
     */
    public static final Mapping CHANGED_BY = Mapping.named("changedBy");
    @NoJournal
    @NullAllowed
    @Length(50)
    @IgnoreInImportChangedCheck
    private String changedBy;

    /**
     * Stores the timestamp when the associated entity was last changed.
     */
    public static final Mapping CHANGED_AT = Mapping.named("changedAt");
    @NoJournal
    @NullAllowed
    @IgnoreInImportChangedCheck
    private LocalDateTime changedAt;

    /**
     * Stores the system string ({@link TaskContext#getSystemString()}) where the associated entity was last changed.
     */
    public static final Mapping CHANGED_IN = Mapping.named("changedIn");
    @NoJournal
    @NullAllowed
    @Length(150)
    @IgnoreInImportChangedCheck
    private String changedIn;

    /**
     * Stores the node string ({@link CallContext#getNodeName()}) where the associated entity was last changed.
     */
    public static final Mapping CHANGED_ON = Mapping.named("changedOn");
    @NoJournal
    @NullAllowed
    @Length(50)
    @IgnoreInImportChangedCheck
    private String changedOn;

    @Transient
    private boolean silent;

    /**
     * Returns a default selection of {@link DateRange date ranges} for a time aggregation on {@link #CHANGED_AT}.
     *
     * @return an array of date ranges to create a time aggregation on the last changed date
     * @see sirius.biz.web.MongoPageHelper#addTimeAggregation(Mapping, boolean, DateRange...)
     * @see sirius.biz.web.ElasticPageHelper#addTimeAggregation(Mapping, boolean, DateRange...)
     */
    public static DateRange[] defaultChangeFilterRanges() {
        return new DateRange[]{DateRange.TODAY,
                               DateRange.YESTERDAY,
                               DateRange.THIS_WEEK,
                               DateRange.LAST_WEEK,
                               DateRange.THIS_MONTH,
                               DateRange.LAST_MONTH,
                               DateRange.THIS_YEAR,
                               DateRange.LAST_YEAR,
                               DateRange.BEFORE_LAST_YEAR};
    }

    @BeforeSave
    protected void update() {
        if (!silent) {
            if (createdAt == null) {
                createdBy = Strings.limit(UserContext.getCurrentUser().getProtocolUsername(), 50);
                createdAt = LocalDateTime.now();
                createdIn = Strings.limit(TaskContext.get().getSystemString(), 150);
                createdOn = Strings.limit(CallContext.getNodeName(), 50);
            }
            updateChangeFields();
        }
    }

    /**
     * Recalculates and updates the change trace fields (changed {@link #CHANGED_BY by}, {@link #CHANGED_AT at},
     * {@link #CHANGED_IN in} and {@link #CHANGED_ON on}).
     * <p>
     * This is automatically called in {@link #update()} when the entity using this composite is created/updated.
     * It can now be called externally in special use-cases, for example when directly updating the trace fields with a
     * MongoDB {@link sirius.db.mongo.Updater}.
     */
    public void updateChangeFields() {
        changedBy = Strings.limit(UserContext.getCurrentUser().getProtocolUsername(), 50);
        changedAt = LocalDateTime.now();
        changedIn = Strings.limit(TaskContext.get().getSystemString(), 150);
        changedOn = Strings.limit(CallContext.getNodeName(), 50);
    }

    /**
     * Checks if the entity this trace is attached to was changed after the provided point in time.
     *
     * @param time the time in point to check against
     * @return <tt>true<tt> if the entity was changed after the point in time, <tt>false</tt> otherwise
     */
    public boolean hasChangedSince(LocalDateTime time) {
        return getChangedAt() != null && getChangedAt().isAfter(time);
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

    public String getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getCreatedIn() {
        return createdIn;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public String getChangedIn() {
        return changedIn;
    }

    public String getCreatedOn() {
        return createdOn;
    }

    public String getChangedOn() {
        return changedOn;
    }
}
