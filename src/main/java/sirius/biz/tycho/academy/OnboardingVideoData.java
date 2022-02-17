/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.academy;

import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.kernel.di.std.Priorized;

import java.time.LocalDateTime;

/**
 * Contains the database independent part of an {@link OnboardingVideo}.
 */
public class OnboardingVideoData extends Composite {

    /**
     * Contains the academy this video belongs to.
     */
    public static final Mapping ACADEMY = Mapping.named("academy");
    @Length(50)
    private String academy;

    /**
     * Contains a random token to detect outdated videos after a sync.
     */
    public static final Mapping SYNC_TOKEN = Mapping.named("syncToken");
    @Length(50)
    private String syncToken;

    /**
     * Contains the id of the onboarding participant.
     * <p>
     * This will most probably be a {@link BaseEntity#getUniqueName()}.
     */
    public static final Mapping OWNER = Mapping.named("owner");
    @Length(64)
    private String owner;

    /**
     * Contains the priority copied from {@link AcademyVideoData#PRIORITY}.
     */
    public static final Mapping PRIORITY = Mapping.named("priority");
    private int priority = Priorized.DEFAULT_PRIORITY;

    /**
     * Assigns a random priority which is updated frequently by a {@link RecomputeOnboardingVideosCheck}.
     * <p>
     * This is used to suggest random but not constantly changing videos. Using this approach we recommend
     * the same video for a day or so instead of suggesting another video for every page load.
     */
    public static final Mapping RANDOM_PRIORITY = Mapping.named("randomPriority");
    private int randomPriority = Priorized.DEFAULT_PRIORITY;

    /**
     * Determines when the recommendation was created.
     */
    public static final Mapping CREATED = Mapping.named("created");
    private LocalDateTime created;

    /**
     * Stores when the recommendation was last updated.
     */
    public static final Mapping LAST_UPDATED = Mapping.named("lastUpdated");
    private LocalDateTime lastUpdated;

    /**
     * Determines if this video has been shown (recommended) in the UI.
     */
    public static final Mapping LAST_SHOWN_IN_UI = Mapping.named("lastShownInUI");
    @NullAllowed
    private LocalDateTime lastShownInUI;

    /**
     * Counts how often this video has been shown (recommended) in the UI.
     */
    public static final Mapping NUM_SHOWN_IN_UI = Mapping.named("numShownInUI");
    private int numShownInUI;

    /**
     * Stores when this video was last recommended per mail.
     */
    public static final Mapping LAST_RECOMMENDED_PER_MAIL = Mapping.named("lastRecommendedPerMail");
    @NullAllowed
    private LocalDateTime lastRecommendedPerMail;

    /**
     * Counts how often this video has been recommended per mail.
     */
    public static final Mapping NUM_RECOMMENDED_PER_MAIL = Mapping.named("numRecommendedPerMail");
    private int numRecommendedPerMail;

    /**
     * Stores when this video was last watched by the owner.
     */
    public static final Mapping LAST_WATCHED = Mapping.named("lastWatched");
    @NullAllowed
    private LocalDateTime lastWatched;

    /**
     * Counts how often this video has been watched.
     */
    public static final Mapping NUM_WATCHED = Mapping.named("numWatched");
    private int numWatched;

    /**
     * Determines how many percent (of the length) of the video has been watched.
     */
    public static final Mapping PERCENT_WATCHED = Mapping.named("percentWatched");
    private int percentWatched;

    /**
     * Records if this video has been watched.
     */
    public static final Mapping WATCHED = Mapping.named("watched");
    private boolean watched;

    /**
     * Records if this video has been skipped by the user.
     */
    public static final Mapping SKIPPED = Mapping.named("skipped");
    private boolean skipped;

    /**
     * Stores if this video has been deleted (e.g. because it was removed from the academy).
     */
    public static final Mapping DELETED = Mapping.named("deleted");
    private boolean deleted;

    /**
     * Determines if this video is recommended for watching.
     * <p>
     * This is a computed field which speeds up common queries.
     */
    public static final Mapping RECOMMENDED = Mapping.named("recommended");
    private boolean recommended;

    @BeforeSave
    protected void onSave() {
        this.recommended = !this.skipped && !this.watched && !this.deleted;
    }

    public String getAcademy() {
        return academy;
    }

    public void setAcademy(String academy) {
        this.academy = academy;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public LocalDateTime getLastShownInUI() {
        return lastShownInUI;
    }

    public void setLastShownInUI(LocalDateTime lastShownInUI) {
        this.lastShownInUI = lastShownInUI;
    }

    public int getNumShownInUI() {
        return numShownInUI;
    }

    public void setNumShownInUI(int numShownInUI) {
        this.numShownInUI = numShownInUI;
    }

    public LocalDateTime getLastRecommendedPerMail() {
        return lastRecommendedPerMail;
    }

    public void setLastRecommendedPerMail(LocalDateTime lastRecommendedPerMail) {
        this.lastRecommendedPerMail = lastRecommendedPerMail;
    }

    public int getNumRecommendedPerMail() {
        return numRecommendedPerMail;
    }

    public void setNumRecommendedPerMail(int numRecommendedPerMail) {
        this.numRecommendedPerMail = numRecommendedPerMail;
    }

    public LocalDateTime getLastWatched() {
        return lastWatched;
    }

    public void setLastWatched(LocalDateTime lastWatched) {
        this.lastWatched = lastWatched;
    }

    public int getNumWatched() {
        return numWatched;
    }

    public void setNumWatched(int numWatched) {
        this.numWatched = numWatched;
    }

    public int getPercentWatched() {
        return percentWatched;
    }

    public void setPercentWatched(int percentWatched) {
        this.percentWatched = percentWatched;
    }

    public boolean isWatched() {
        return watched;
    }

    public void setWatched(boolean watched) {
        this.watched = watched;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public void setSkipped(boolean skipped) {
        this.skipped = skipped;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public boolean isRecommended() {
        return recommended;
    }

    public void setRecommended(boolean recommended) {
        this.recommended = recommended;
    }

    public int getRandomPriority() {
        return randomPriority;
    }

    public void setRandomPriority(int randomPriority) {
        this.randomPriority = randomPriority;
    }

    public String getSyncToken() {
        return syncToken;
    }

    public void setSyncToken(String syncToken) {
        this.syncToken = syncToken;
    }
}
