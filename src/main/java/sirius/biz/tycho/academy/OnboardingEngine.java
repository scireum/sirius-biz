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
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.settings.Extension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public abstract class OnboardingEngine {

    @Part
    private ExecutionFlags executionFlags;

    @Part
    private GlobalContext context;

    private Cache<String, AcademyVideo> academyVideoCache = CacheManager.createLocalCache("tycho-academy-videos");

    protected List<? extends AcademyVideo> fetchAcademyVideos(String academy) {
        if (!executionFlags.wasExecuted("_ONBOARDING-" + academy, "fetched", Duration.ofHours(23))) {
            try {
                loadVideosForAcademy(academy);
                executionFlags.storeExecutionFlag("_ONBOARDING-" + academy,
                                                  "fetched",
                                                  LocalDateTime.now(),
                                                  Period.ofDays(2));
            } catch (Exception e) {
                Exceptions.handle(Log.BACKGROUND, e);
            }
        }

        return queryAcademyVideos(academy);
    }

    protected void loadVideosForAcademy(String academy) throws Exception {
        Extension config = Sirius.getSettings().getExtension("tycho.onboarding.academies", academy);

        Set<String> currentVideoIds = fetchCurrentAcademyVideoIds(academy);
        AcademyProvider provider = context.findPart(config.require("provider").asString(), AcademyProvider.class);
        provider.fetchVideos(academy, config, video -> {
            video.setAcademy(academy);
            persistAcademyVideo(video);
            currentVideoIds.remove(video.getVideoId());
        });
        markOutdatedAcademyVideosAsDeleted(academy, currentVideoIds);
    }

    public AcademyVideo fetchAcademyVideo(String videoId) {
        return academyVideoCache.get(videoId, this::fetchAcademyVideoFromDatabase);
    }

    protected abstract Set<String> fetchCurrentAcademyVideoIds(String academy);

    protected abstract void persistAcademyVideo(AcademyVideoData video);

    protected abstract void markOutdatedAcademyVideosAsDeleted(String academy, Set<String> videoIds);

    protected abstract List<? extends AcademyVideo> queryAcademyVideos(String academy);

    protected abstract AcademyVideo fetchAcademyVideoFromDatabase(String videoId);

    protected abstract Set<String> fetchCurrentOnboardingVideoIds(String academy, String owner);

    protected abstract OnboardingVideo createOrUpdateOnboardingVideo(String owner,
                                                                     AcademyVideo academyVideo);

    protected abstract void markOutdatedOnboardingVideosAsDeleted(String academy, String owner, Set<String> videoIds);

    protected abstract void deleteOnboardingVideosFor(String owner);

    public abstract Optional<? extends OnboardingVideo> fetchSomeVideo(String owner);

    public abstract List<? extends OnboardingVideo> fetchVideos(String owner, String trackId);

    public abstract List<AcademyTrackInfo> fetchTracks(String owner);

    public abstract OnboardingVideo fetchVideo(String owner, String videoId);

    public abstract List<? extends OnboardingVideo> fetchOtherRecommendations(String owner, String videoId);

    public abstract void recordVideoShown(String owner, String videoId);

    public abstract void recordVideoStarted(String owner, String videoId);

    public abstract void updateWachedPercent(String owner, String videoId, int seenInPercent);

    public abstract void markAsSkipped(String owner, String videoId);
}
