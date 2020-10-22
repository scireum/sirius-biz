/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.academy;

import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;

import java.time.LocalDateTime;

public class OnboardingVideoData extends Composite {

    public static final Mapping ACADEMY = Mapping.named("academy");
    @Length(50)
    private String academy;

    public static final Mapping OWNER = Mapping.named("owner");
    @Length(64)
    private String owner;

    public static final Mapping PRIORITY = Mapping.named("priority");
    private int priority;

    public static final Mapping RANDOM_PRIORITY = Mapping.named("randomPriority");
    private int randomPriority;

    public static final Mapping CREATED = Mapping.named("created");
    private LocalDateTime created;

    public static final Mapping LAST_UPDATED = Mapping.named("lastUpdated");
    private LocalDateTime lastUpdated;

    public static final Mapping LAST_SHOWN_IN_UI = Mapping.named("lastShownInUI");
    @NullAllowed
    private LocalDateTime lastShownInUI;

    public static final Mapping NUM_SHOWN_IN_UI = Mapping.named("numShownInUI");
    private int numShownInUI;

    public static final Mapping LAST_RECOMMENDED_PER_MAIL = Mapping.named("lastRecommendedPerMail");
    @NullAllowed
    private LocalDateTime lastRecommendedPerMail;

    public static final Mapping NUM_RECOMMENDED_PER_MAIL = Mapping.named("numRecommendedPerMail");
    private int numRecommendedPerMail;

    public static final Mapping LAST_WATCHED = Mapping.named("lastWatched");
    @NullAllowed
    private LocalDateTime lastWatched;

    public static final Mapping NUM_WATCHED = Mapping.named("numWatched");
    private int numWatched;

    public static final Mapping PERCENT_WATCHED = Mapping.named("percentWatched");
    private int percentWatched;

    public static final Mapping WATCHED = Mapping.named("watched");
    private boolean watched;

    public static final Mapping SKIPPED = Mapping.named("skipped");
    private boolean skipped;

    public static final Mapping DELETED = Mapping.named("deleted");
    private boolean deleted;

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
}
