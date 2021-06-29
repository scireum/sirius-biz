/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.academy;

import sirius.kernel.commons.Strings;

/**
 * Represents a track (collection of associated videos) which the {@link OnboardingEngine} provides for a participant.
 */
public class AcademyTrackInfo {

    private final String trackId;
    private final String trackName;
    private final String trackDescription;
    private int numberOfVideos;
    private int numberOfRecommendedVideos;
    private int totalDuration;

    /**
     * Creates a new track based on the given data.
     *
     * @param trackId          contains the internal id used for filtering
     * @param trackName        contains the name of the track which is shown to the user
     * @param trackDescription contains the description of the track
     */
    public AcademyTrackInfo(String trackId, String trackName, String trackDescription) {
        this.trackId = trackId;
        this.trackName = trackName;
        this.trackDescription = trackDescription;
    }

    public void incRecommendedVideos() {
        numberOfRecommendedVideos++;
    }

    public void incVideos() {
        numberOfVideos++;
    }

    public String getTrackId() {
        return trackId;
    }

    public String getTrackName() {
        return trackName;
    }

    public String getTrackDescription() {
        return trackDescription;
    }

    public int getNumberOfVideos() {
        return numberOfVideos;
    }

    public int getNumberOfRecommendedVideos() {
        return numberOfRecommendedVideos;
    }

    public void incDuration(int duration) {
        this.totalDuration += duration;
    }

    public int getTotalDuration() {
        return totalDuration;
    }

    public String getTotalDurationAsString() {
        long minutes = totalDuration / 60;
        long seconds = totalDuration % 60;

        return Strings.apply("%02d:%02d", minutes, seconds);
    }
}
