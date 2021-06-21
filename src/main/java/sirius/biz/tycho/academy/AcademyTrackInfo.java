/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.academy;

/**
 * Represents a track (collection of associated videos) which the {@link OnboardingEngine} provides for a participant.
 */
public class AcademyTrackInfo {

    private final String trackId;
    private final String trackName;
    private final int numberOfVideos;
    private final int numberOfRecommendedVideos;

    /**
     * Creates a new track based on the given data.
     *
     * @param trackId                   contains the internal id used for filtering
     * @param trackName                 contains the name of the track which is shown to the user
     * @param numberOfVideos            contains the number of videos in this track
     * @param numberOfRecommendedVideos contains the number of recommended videos in this track
     */
    public AcademyTrackInfo(String trackId, String trackName, int numberOfVideos, int numberOfRecommendedVideos) {
        this.trackId = trackId;
        this.trackName = trackName;
        this.numberOfVideos = numberOfVideos;
        this.numberOfRecommendedVideos = numberOfRecommendedVideos;
    }

    public String getTrackId() {
        return trackId;
    }

    public String getTrackName() {
        return trackName;
    }

    public int getNumberOfVideos() {
        return numberOfVideos;
    }

    public int getNumberOfRecommendedVideos() {
        return numberOfRecommendedVideos;
    }
}
