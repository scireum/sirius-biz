/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.academy.mongo;

import sirius.biz.tycho.academy.AcademyVideo;
import sirius.biz.tycho.academy.AcademyVideoData;
import sirius.biz.tycho.academy.OnboardingEngine;
import sirius.biz.tycho.academy.OnboardingVideo;
import sirius.biz.tycho.academy.OnboardingVideoData;
import sirius.db.mongo.Mango;
import sirius.db.mongo.Mongo;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Provides an implementation of the {@link OnboardingEngine} based on MongoDB.
 */
@Register(classes = OnboardingEngine.class, framework = MongoOnboardingEngine.FRAMEWORK_TYCHO_MONGO_ACADEMIES)
public class MongoOnboardingEngine extends OnboardingEngine {

    /**
     * Specifies the framework which must be enabled to use MongoDB based video academies.
     */
    public static final String FRAMEWORK_TYCHO_MONGO_ACADEMIES = "tycho.academies-mongo";

    @Part
    private Mango mango;

    @Part
    private Mongo mongo;

    @Override
    protected void persistAcademyVideo(AcademyVideoData videoData) {
        MongoAcademyVideo video = mango.select(MongoAcademyVideo.class)
                                       .eq(MongoAcademyVideo.ACADEMY_VIDEO_DATA.inner(AcademyVideoData.ACADEMY),
                                           videoData.getAcademy())
                                       .eq(MongoAcademyVideo.ACADEMY_VIDEO_DATA.inner(AcademyVideoData.VIDEO_ID),
                                           videoData.getVideoId())
                                       .first()
                                       .orElseGet(MongoAcademyVideo::new);
        if (video.getAcademyVideoData().getCreated() == null) {
            video.getAcademyVideoData().setCreated(LocalDateTime.now());
        }

        video.getAcademyVideoData().setLastUpdated(LocalDateTime.now());
        video.getAcademyVideoData().loadFrom(videoData);
        mango.update(video);
    }

    @Override
    protected void markOutdatedAcademyVideosAsDeleted(String academy, String tokenToSkip) {
        mango.select(MongoAcademyVideo.class)
             .eq(MongoAcademyVideo.ACADEMY_VIDEO_DATA.inner(AcademyVideoData.ACADEMY), academy)
             .ne(MongoAcademyVideo.ACADEMY_VIDEO_DATA.inner(AcademyVideoData.SYNC_TOKEN), tokenToSkip)
             .iterateAll(video -> {
                 try {
                     video.getAcademyVideoData().setLastUpdated(LocalDateTime.now());
                     video.getAcademyVideoData().setDeleted(true);
                     mango.update(video);
                 } catch (Exception e) {
                     Exceptions.handle(Log.BACKGROUND, e);
                 }
             });
    }

    @Override
    protected List<? extends AcademyVideo> queryAcademyVideos(String academy) {
        List<MongoAcademyVideo> videos = new ArrayList<>();
        mango.select(MongoAcademyVideo.class)
             .eq(MongoAcademyVideo.ACADEMY_VIDEO_DATA.inner(AcademyVideoData.ACADEMY), academy)
             .eq(MongoAcademyVideo.ACADEMY_VIDEO_DATA.inner(AcademyVideoData.DELETED), false)
             .iterateAll(videos::add);

        return videos;
    }

    @Override
    protected AcademyVideo fetchAcademyVideoFromDatabase(String videoId) {
        return mango.find(MongoAcademyVideo.class, videoId).orElseGet(MongoAcademyVideo::new);
    }

    @Override
    protected OnboardingVideo createOrUpdateOnboardingVideo(String owner, AcademyVideo academyVideo, String syncToken) {
        MongoOnboardingVideo onboardingVideo = mango.select(MongoOnboardingVideo.class)
                                                    .eq(MongoOnboardingVideo.ACADEMY_VIDEO, academyVideo)
                                                    .eq(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(
                                                            OnboardingVideoData.OWNER), owner)
                                                    .first()
                                                    .orElseGet(() -> {
                                                        MongoOnboardingVideo newVideo = new MongoOnboardingVideo();
                                                        newVideo.getAcademyVideo()
                                                                .setId(((MongoAcademyVideo) academyVideo).getId());
                                                        newVideo.getOnboardingVideoData()
                                                                .setCreated(LocalDateTime.now());
                                                        return newVideo;
                                                    });

        AcademyVideoData academyVideoData = academyVideo.getAcademyVideoData();
        OnboardingVideoData onboardingVideoData = onboardingVideo.getOnboardingVideoData();
        onboardingVideoData.setAcademy(academyVideoData.getAcademy());
        onboardingVideo.setTrackId(academyVideoData.getTrackId());
        onboardingVideo.setVideoCode(academyVideoData.getVideoCode());
        onboardingVideoData.setPriority(academyVideoData.getPriority());
        onboardingVideoData.setLastUpdated(LocalDateTime.now());
        onboardingVideoData.setRandomPriority(ThreadLocalRandom.current().nextInt(99999));
        onboardingVideoData.setDeleted(false);
        onboardingVideoData.setOwner(owner);
        onboardingVideoData.setSyncToken(syncToken);

        mango.update(onboardingVideo);

        return onboardingVideo;
    }

    @Override
    public void markOutdatedOnboardingVideosAsDeleted(String academy, String owner, String tokenToSkip) {
        mango.select(MongoOnboardingVideo.class)
             .eq(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.ACADEMY), academy)
             .eq(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.OWNER), owner)
             .ne(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.SYNC_TOKEN), tokenToSkip)
             .iterateAll(video -> {
                 try {
                     video.getOnboardingVideoData().setLastUpdated(LocalDateTime.now());
                     video.getOnboardingVideoData().setDeleted(true);
                     mango.update(video);
                 } catch (Exception e) {
                     Exceptions.handle(Log.BACKGROUND, e);
                 }
             });
    }

    @Override
    public void deleteOnboardingVideosFor(String owner) {
        mango.select(MongoOnboardingVideo.class)
             .eq(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.OWNER), owner)
             .delete();
    }

    @Override
    protected void forAllVideos(String owner, Consumer<? super OnboardingVideo> videoCallback) {
        mango.select(MongoOnboardingVideo.class)
             .eq(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.OWNER), owner)
             .eq(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.DELETED), false)
             .orderAsc(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.PRIORITY))
             .iterateAll(videoCallback::accept);
    }

    @Override
    public Optional<? extends OnboardingVideo> fetchSomeVideo(String owner) {
        return mango.select(MongoOnboardingVideo.class)
                    .eq(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.OWNER), owner)
                    .eq(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.RECOMMENDED), true)
                    .orderAsc(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.RANDOM_PRIORITY))
                    .first();
    }

    @Override
    public List<? extends OnboardingVideo> fetchVideos(String owner, String trackId) {
        return mango.select(MongoOnboardingVideo.class)
                    .eq(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.OWNER), owner)
                    .eq(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.DELETED), false)
                    .eq(MongoOnboardingVideo.TRACK_ID, trackId)
                    .orderAsc(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.PRIORITY))
                    .queryList();
    }

    @Override
    public OnboardingVideo fetchVideo(String owner, String videoId) {
        return mango.select(MongoOnboardingVideo.class)
                    .eq(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.OWNER), owner)
                    .eq(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.DELETED), false)
                    .eq(MongoOnboardingVideo.ID, videoId)
                    .queryFirst();
    }

    @Nullable
    @Override
    public OnboardingVideo fetchVideoByCode(String owner, String videoCode) {
        return mango.select(MongoOnboardingVideo.class)
                    .eq(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.OWNER), owner)
                    .eq(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.DELETED), false)
                    .eq(MongoOnboardingVideo.VIDEO_CODE, videoCode)
                    .orderDesc(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.LAST_UPDATED))
                    .queryFirst();
    }

    @Override
    public List<? extends OnboardingVideo> fetchOtherRecommendations(String owner, String videoId, String tackId) {
        return mango.select(MongoOnboardingVideo.class)
                    .eq(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.OWNER), owner)
                    .ne(MongoOnboardingVideo.ID, videoId)
                    .eq(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.RECOMMENDED), true)
                    .orderAsc(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.RANDOM_PRIORITY))
                    .limit(NUMBER_OF_VIDEOS_TO_RECOMMEND)
                    .queryList();
    }

    @Override
    public void recordVideoShown(String owner, String videoId) {
        mongo.update()
             .set(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.LAST_SHOWN_IN_UI),
                  LocalDateTime.now())
             .inc(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.NUM_SHOWN_IN_UI), 1)
             .where(MongoOnboardingVideo.ID, videoId)
             .executeForOne(MongoOnboardingVideo.class);
    }

    @Override
    public void recordVideoStarted(String owner, String videoId) {
        mongo.update()
             .set(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.LAST_WATCHED),
                  LocalDateTime.now())
             .inc(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.NUM_WATCHED), 1)
             .where(MongoOnboardingVideo.ID, videoId)
             .executeForOne(MongoOnboardingVideo.class);
    }

    @Override
    public void updateWatchedPercent(String owner, String videoId, int seenInPercent) {
        MongoOnboardingVideo video = mango.select(MongoOnboardingVideo.class)
                                          .fields(MongoOnboardingVideo.ID,
                                                  MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.PERCENT_WATCHED))
                                          .eq(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.OWNER),
                                              owner)
                                          .eq(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.DELETED),
                                              false)
                                          .eq(MongoOnboardingVideo.ID, videoId)
                                          .queryFirst();

        if (video == null) {
            return;
        }

        if (video.getOnboardingVideoData().getPercentWatched() > seenInPercent) {
            return;
        }

        boolean markAsWatched = seenInPercent >= MIN_PERCENT_TO_CONSIDER_VIDEO_WATCHED;
        mongo.update()
             .set(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.PERCENT_WATCHED), seenInPercent)
             .set(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.SKIPPED), false)
             .set(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.RECOMMENDED), !markAsWatched)
             .set(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.WATCHED), markAsWatched)
             .where(MongoOnboardingVideo.ID, videoId)
             .executeForOne(MongoOnboardingVideo.class);
    }

    @Override
    public void markAsSkipped(String owner, String videoId) {
        mongo.update()
             .set(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.RECOMMENDED), false)
             .set(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.SKIPPED), true)
             .where(MongoOnboardingVideo.ID, videoId)
             .where(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.WATCHED), false)
             .executeForOne(MongoOnboardingVideo.class);
    }
}
