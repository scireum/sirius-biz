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
import sirius.db.mixing.annotations.AfterDelete;
import sirius.db.mixing.annotations.Transient;
import sirius.kernel.di.std.Part;

/**
 * Contains a bunch of metrics stored for each {@link OnboardingParticipant}.
 */
public class OnboardingData extends Composite {

    /**
     * Contains the ratio of watched (vs. available) videos.
     */
    public static final Mapping PERCENT_WATCHED = Mapping.named("percentWatched");
    private int percentWatched;

    /**
     * Contains the ratio of skipped (vs. available) videos.
     */
    public static final Mapping PERCENT_SKIPPED = Mapping.named("percentSkipped");
    private int percentSkipped;

    /**
     * Contains the ratio of watched (vs. available) recommended videos.
     */
    public static final Mapping EDUCATION_LEVEL_PERCENT = Mapping.named("educationLevelPercent");
    private int educationLevelPercent;

    /**
     * Contains the total number of available unwatched videos.
     */
    public static final Mapping NUM_WATCHABLE_VIDEOS = Mapping.named("numWatchableVideos");
    private int numWatchableVideos;

    /**
     * Contains the total number of available videos.
     */
    public static final Mapping NUM_AVAILABLE_VIDEOS = Mapping.named("numAvailableVideos");
    private int numAvailableVideos;

    /**
     * Contains the total number of available recommended/mandatory videos.
     */
    public static final Mapping NUM_MANDATORY_AVAILABLE_VIDEOS = Mapping.named("numMandatoryAvailableVideos");
    private int numMandatoryAvailableVideos;

    @Transient
    private final BaseEntity<?> owner;

    @Part
    private static OnboardingEngine onboardingEngine;

    /**
     * Creates a new composite for the given owner.
     *
     * @param owner the owner which is the actual participant
     */
    public OnboardingData(BaseEntity<?> owner) {
        this.owner = owner;
    }

    @AfterDelete
    protected void deleteVideos() {
        onboardingEngine.deleteOnboardingVideosFor(owner.getIdAsString());
    }

    public int getPercentWatched() {
        return percentWatched;
    }

    public void setPercentWatched(int percentWatched) {
        this.percentWatched = percentWatched;
    }

    public int getPercentSkipped() {
        return percentSkipped;
    }

    public void setPercentSkipped(int percentSkipped) {
        this.percentSkipped = percentSkipped;
    }

    public int getEducationLevelPercent() {
        return educationLevelPercent;
    }

    public void setEducationLevelPercent(int educationLevelPercent) {
        this.educationLevelPercent = educationLevelPercent;
    }

    public int getNumWatchableVideos() {
        return numWatchableVideos;
    }

    public void setNumWatchableVideos(int numWatchableVideos) {
        this.numWatchableVideos = numWatchableVideos;
    }

    public int getNumAvailableVideos() {
        return numAvailableVideos;
    }

    public void setNumAvailableVideos(int numAvailableVideos) {
        this.numAvailableVideos = numAvailableVideos;
    }

    public int getNumMandatoryAvailableVideos() {
        return numMandatoryAvailableVideos;
    }

    public void setNumMandatoryAvailableVideos(int numMandatoryAvailableVideos) {
        this.numMandatoryAvailableVideos = numMandatoryAvailableVideos;
    }
}
