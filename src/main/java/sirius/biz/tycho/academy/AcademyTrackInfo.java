/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.academy;

public class AcademyTrackInfo {

    private String trackId;
    private String trackName;
    private int numberOfVideos;
    private int numberOfRecommendedVideos;

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
