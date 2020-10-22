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
import sirius.db.mixing.annotations.AfterDelete;
import sirius.db.mixing.annotations.Transient;
import sirius.kernel.di.std.Part;

public class OnboardingData extends Composite {

    private int percentWatched;

    private int percentSkipped;

    private int educationLevelPercent;

    private int numWatchableVideos;

    private int numAvailableVideos;

    private int numMandatoryAvailableVideos;

    @Transient
    private BaseEntity<?> owner;

    @Part
    private static OnboardingEngine onboardingEngine;

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
