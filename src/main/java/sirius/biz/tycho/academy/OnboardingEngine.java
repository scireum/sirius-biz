/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.academy;

import sirius.biz.analytics.flags.ExecutionFlags;
import sirius.kernel.Sirius;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.settings.Extension;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Manages{@link OnboardingVideo onboarding videos} and {@link AcademyVideo academy videos}.
 * <p>
 * Note that this is actually entity agnostic. The target entities are determined by subclassing
 * {@link RecomputeOnboardingVideosCheck}.
 */
public abstract class OnboardingEngine {

    private static final String ACADEMY_VIDEOS_FETCHED_FLAG = "fetched";
    private static final Duration ACADEMY_VIDEO_REFRESH_INTERVAL = Duration.ofHours(23);

    protected static final int NUMBER_OF_VIDEOS_TO_RECOMMEND = 4;
    protected static final int MIN_PERCENT_TO_CONSIDER_VIDEO_WATCHED = 40;

    @Part
    private ExecutionFlags executionFlags;

    @Part
    private GlobalContext context;

    private final Cache<String, AcademyVideo> academyVideoCache = CacheManager.createLocalCache("tycho-academy-videos");

    /**
     * Fetches the list of available academy videos for the given academy.
     * <p>
     * Note that the list of videos might be persisted for a certain amount of time.
     *
     * @param academy the academy to fetch videos for
     * @return the list of videos in the given academy
     */
    protected List<? extends AcademyVideo> fetchAcademyVideos(String academy) {
        String executionFlag = "_ONBOARDING-" + academy;
        Duration refreshInterval = Sirius.isDev() ? Duration.ofSeconds(30) : ACADEMY_VIDEO_REFRESH_INTERVAL;
        if (!executionFlags.wasExecuted(executionFlag, ACADEMY_VIDEOS_FETCHED_FLAG, refreshInterval)) {
            try {
                loadVideosForAcademy(academy);
                executionFlags.storeExecutionFlag(executionFlag,
                                                  ACADEMY_VIDEOS_FETCHED_FLAG,
                                                  LocalDateTime.now(),
                                                  Period.ofDays(2));
            } catch (Exception e) {
                Exceptions.handle(Log.BACKGROUND, e);
            }
        }

        return queryAcademyVideos(academy);
    }

    private void loadVideosForAcademy(String academy) throws Exception {
        try {
            String syncToken = Strings.generateCode(32);
            Tuple<Extension, AcademyProvider> settings = fetchAcademySettings(academy);
            settings.getSecond().fetchVideos(academy, settings.getFirst(), video -> {
                video.setAcademy(academy);
                video.setSyncToken(syncToken);
                persistAcademyVideo(video);
            });
            markOutdatedAcademyVideosAsDeleted(academy, syncToken);
        } finally {
            academyVideoCache.clear();
        }
    }

    /**
     * Fetches the settings for the given academy.
     *
     * @param academy the name of the academy to fetch the settings for
     * @return the settings and the provider of the given academy
     */
    public Tuple<Extension, AcademyProvider> fetchAcademySettings(String academy) {
        Extension settings = Sirius.getSettings().getExtension("tycho.onboarding.academies", academy);
        return Tuple.create(settings, context.findPart(settings.require("provider").asString(), AcademyProvider.class));
    }

    /**
     * Fetches the academy video with the given id.
     *
     * @param videoId the id of the video to fetch
     * @return the video with the given id or <tt>null</tt> if no matching video was found
     */
    @Nullable
    public AcademyVideo fetchAcademyVideo(String videoId) {
        return academyVideoCache.get(videoId, this::fetchAcademyVideoFromDatabase);
    }

    /**
     * Persists the given video data in the database.
     *
     * @param video the video data to store
     */
    protected abstract void persistAcademyVideo(AcademyVideoData video);

    /**
     * Deletes all outdated academy videos.
     *
     * @param academy           the academy to update the videos for
     * @param syncTokenToIgnore the last valid sync token. All videos which contain another token, are considered
     *                          outdated and will be marked as deleted.
     */
    protected abstract void markOutdatedAcademyVideosAsDeleted(String academy, String syncTokenToIgnore);

    /**
     * Lists all videos of the given academy.
     *
     * @param academy the academy to fetch the videos for
     * @return the videos in the given academy
     */
    protected abstract List<? extends AcademyVideo> queryAcademyVideos(String academy);

    /**
     * Actually pulls the metadata of the requested academy video from the database.
     *
     * @param videoId the id of the video to load
     * @return the loaded video or <tt>null</tt> if the video is unknown
     */
    @Nullable
    protected abstract AcademyVideo fetchAcademyVideoFromDatabase(String videoId);

    /**
     * Creates or find the onboarding video for the given owner/target and academy video.
     *
     * @param owner        the onboarding participant which is targeted by the video
     * @param academyVideo the video to show to the participant
     * @param syncToken    the token to store in the onboarding video to support updates / cleanups
     * @return the onboarding video for the given academy video and target
     */
    protected abstract OnboardingVideo createOrUpdateOnboardingVideo(String owner,
                                                                     AcademyVideo academyVideo,
                                                                     String syncToken);

    /**
     * Marks all untouched onboarding videos as outdated.
     *
     * @param academy           the academy to filter on
     * @param owner             the owner to filter on
     * @param syncTokenToIgnore the token which is considered valid, all videos with other tokens are considered
     *                          outdated and will me marked as deleted
     */
    protected abstract void markOutdatedOnboardingVideosAsDeleted(String academy,
                                                                  String owner,
                                                                  String syncTokenToIgnore);

    /**
     * Wipes all onboarding videos for the given target/owner.
     *
     * @param owner the owner to delete all videos for
     */
    protected abstract void deleteOnboardingVideosFor(String owner);

    /**
     * Iterates over all videos of the given owner.
     *
     * @param owner         the onboarding participant to filter on
     * @param videoCallback the callback to be supplied with all videos
     */
    protected abstract void forAllVideos(String owner, Consumer<? super OnboardingVideo> videoCallback);

    /**
     * Fetches metadata for the track (and owner).
     *
     * @param owner   the onboarding participant to filter on
     * @param trackId the track to fetch metadata for
     * @return the metadata for the given track and owner
     */
    public Optional<AcademyTrackInfo> fetchTrack(String owner, String trackId) {
        List<? extends OnboardingVideo> videos = fetchVideos(owner, trackId);
        if (videos.isEmpty()) {
            return Optional.empty();
        }

        OnboardingVideo example = videos.get(0);
        AcademyVideoData exampleAcademyVideoData = example.fetchAcademyVideoData();
        AcademyTrackInfo track = new AcademyTrackInfo(exampleAcademyVideoData.getTrackId(),
                                                      exampleAcademyVideoData.getTrackName(),
                                                      exampleAcademyVideoData.getTrackDescription());
        for (OnboardingVideo video : videos) {
            if (video.getOnboardingVideoData().isRecommended()) {
                track.incRecommendedVideos();
            }
            track.incVideos();
            track.incDuration(exampleAcademyVideoData.getDuration());
        }

        return Optional.of(track);
    }

    /**
     * Fetches all available tracks for the given owner.
     *
     * @param owner the owner/participant to fetch tracks for
     * @return the list of all tracks visible to the given owner
     */
    public Collection<AcademyTrackInfo> fetchTracks(String owner) {
        Map<String, AcademyTrackInfo> tracks = new LinkedHashMap<>();
        forAllVideos(owner, video -> {
            AcademyVideoData academyVideoData = video.fetchAcademyVideoData();
            AcademyTrackInfo track = tracks.computeIfAbsent(academyVideoData.getTrackId(),
                                                            ignored -> new AcademyTrackInfo(academyVideoData.getTrackId(),
                                                                                            academyVideoData.getTrackName(),
                                                                                            academyVideoData.getTrackDescription()));

            if (video.getOnboardingVideoData().isRecommended()) {
                track.incRecommendedVideos();
            }
            track.incVideos();
            track.incDuration(academyVideoData.getDuration());
        });

        return tracks.values();
    }

    /**
     * Fetches a randomly selected but recommended video for the given owner.
     *
     * @param owner the owner/participant to fetch a video for
     * @return a recommended video, or an empty optional if none is available.
     */
    public abstract Optional<? extends OnboardingVideo> fetchSomeVideo(String owner);

    /**
     * Fetches all videos for the given owner and track.
     *
     * @param owner   the owner/participant to fetch videos for
     * @param trackId the track to filter on
     * @return the list of videos in the given track
     */
    public abstract List<? extends OnboardingVideo> fetchVideos(String owner, String trackId);

    /**
     * Fetches the actual onboarding video for the given owner and id.
     *
     * @param owner   the owner/participant to fetch the video for
     * @param videoId the id of the video to fetch
     * @return the video with the given id or <tt>null</tt> if it wasn't found
     */
    @Nullable
    public abstract OnboardingVideo fetchVideo(String owner, String videoId);

    /**
     * Fetches other recommendations to show next to the given video.
     *
     * @param owner   the owner/participant to fetch videos for
     * @param videoId the id of the video being played
     * @param trackId the track of the given video
     * @return some recommendation to be shown next to the given video
     */
    public abstract List<? extends OnboardingVideo> fetchOtherRecommendations(String owner,
                                                                              String videoId,
                                                                              String trackId);

    /**
     * Stores that the video has been shown to the owner.
     *
     * @param owner   the owner/participant
     * @param videoId the id of the video which has been shown
     */
    public abstract void recordVideoShown(String owner, String videoId);

    /**
     * Stores that the video has actually started playing for the given owner.
     *
     * @param owner   the owner/participant
     * @param videoId the id of the video which has been started
     */
    public abstract void recordVideoStarted(String owner, String videoId);

    /**
     * Updates the relative duration which has been watched.
     *
     * @param owner         the owner/participant
     * @param videoId       the video to record the percentage for
     * @param seenInPercent the duration (in percent) which has been watched by the user
     */
    public abstract void updateWatchedPercent(String owner, String videoId, int seenInPercent);

    /**
     * Records that the owner decided to skip the given video.
     *
     * @param owner   the owner/participant
     * @param videoId the id of the video to mark as skipped
     */
    public abstract void markAsSkipped(String owner, String videoId);
}
