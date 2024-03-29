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

    protected void incRecommendedVideos() {
        numberOfRecommendedVideos++;
    }

    protected void incVideos() {
        numberOfVideos++;
    }

    protected void incDuration(int duration) {
        this.totalDuration += duration;
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

    public int getTotalDuration() {
        return totalDuration;
    }

    /**
     * Provides a human-readable representation of the duration.
     *
     * @return a nicely formatted string representing the duration of the video
     */
    public String generateDurationString() {
        long minutes = totalDuration / 60;
        long seconds = totalDuration % 60;

        return Strings.apply("%02d:%02d", minutes, seconds);
    }
}
