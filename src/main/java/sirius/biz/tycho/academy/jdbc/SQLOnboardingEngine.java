/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.academy.jdbc;

import sirius.biz.tycho.academy.AcademyTrackInfo;
import sirius.biz.tycho.academy.AcademyVideo;
import sirius.biz.tycho.academy.AcademyVideoData;
import sirius.biz.tycho.academy.OnboardingEngine;
import sirius.biz.tycho.academy.OnboardingVideo;
import sirius.biz.tycho.academy.OnboardingVideoData;
import sirius.db.jdbc.OMA;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Register(classes = OnboardingEngine.class)
public class SQLOnboardingEngine extends OnboardingEngine {

    @Part
    private OMA oma;

    @Override
    protected Set<String> fetchCurrentAcademyVideoIds(String academy) {
        return oma.select(SQLAcademyVideo.class)
                  .eq(SQLAcademyVideo.ACADEMY_VIDEO_DATA.inner(AcademyVideoData.ACADEMY), academy)
                  .fields(SQLAcademyVideo.ACADEMY_VIDEO_DATA.inner(AcademyVideoData.VIDEO_ID))
                  .queryList()
                  .stream()
                  .map(v -> v.getAcademyVideoData().getVideoId())
                  .collect(Collectors.toSet());
    }

    @Override
    protected void persistAcademyVideo(AcademyVideoData videoData) {
        SQLAcademyVideo video = oma.select(SQLAcademyVideo.class)
                                   .eq(SQLAcademyVideo.ACADEMY_VIDEO_DATA.inner(AcademyVideoData.ACADEMY),
                                       videoData.getAcademy())
                                   .eq(SQLAcademyVideo.ACADEMY_VIDEO_DATA.inner(AcademyVideoData.VIDEO_ID),
                                       videoData.getVideoId())
                                   .first()
                                   .orElseGet(SQLAcademyVideo::new);
        if (video.getAcademyVideoData().getCreated() == null) {
            video.getAcademyVideoData().setCreated(LocalDateTime.now());
        }

        video.getAcademyVideoData().setLastUpdated(LocalDateTime.now());
        video.getAcademyVideoData().loadFrom(videoData);
        oma.update(video);
    }

    @Override
    protected void markOutdatedAcademyVideosAsDeleted(String academy, Set<String> videoIds) {
        for (String videoId : videoIds) {
            oma.select(SQLAcademyVideo.class)
               .eq(SQLAcademyVideo.ACADEMY_VIDEO_DATA.inner(AcademyVideoData.ACADEMY), academy)
               .eq(SQLAcademyVideo.ACADEMY_VIDEO_DATA.inner(AcademyVideoData.VIDEO_ID), videoId)
               .first()
               .ifPresent(video -> {
                   try {
                       video.getAcademyVideoData().setLastUpdated(LocalDateTime.now());
                       video.getAcademyVideoData().setDeleted(true);
                       oma.update(video);
                   } catch (Exception e) {
                       Exceptions.handle(Log.BACKGROUND, e);
                   }
               });
        }
    }

    @Override
    protected List<? extends AcademyVideo> queryAcademyVideos(String academy) {
        return oma.select(SQLAcademyVideo.class)
                  .eq(SQLAcademyVideo.ACADEMY_VIDEO_DATA.inner(AcademyVideoData.ACADEMY), academy)
                  .eq(SQLAcademyVideo.ACADEMY_VIDEO_DATA.inner(AcademyVideoData.DELETED), false)
                  .queryList();
    }

    @Override
    protected AcademyVideo fetchAcademyVideoFromDatabase(String videoId) {
        return oma.find(SQLAcademyVideo.class, videoId).orElseGet(SQLAcademyVideo::new);
    }

    @Override
    public Set<String> fetchCurrentOnboardingVideoIds(String academy, String owner) {
        return oma.select(SQLOnboardingVideo.class)
                  .eq(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.ACADEMY), academy)
                  .eq(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.OWNER), owner)
                  .fields(SQLOnboardingVideo.ID)
                  .queryList()
                  .stream()
                  .map(SQLOnboardingVideo::getIdAsString)
                  .collect(Collectors.toSet());
    }

    @Override
    protected OnboardingVideo createOrUpdateOnboardingVideo(String owner, AcademyVideo academyVideo) {
        SQLOnboardingVideo onboardingVideo = oma.select(SQLOnboardingVideo.class)
                                                .eq(SQLOnboardingVideo.ACADEMY_VIDEO, academyVideo)
                                                .eq(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.OWNER),
                                                    owner)
                                                .first()
                                                .orElseGet(() -> {
                                                    SQLOnboardingVideo newVideo = new SQLOnboardingVideo();
                                                    newVideo.getAcademyVideo()
                                                            .setId(((SQLAcademyVideo) academyVideo).getId());
                                                    newVideo.getOnboardingVideoData().setCreated(LocalDateTime.now());
                                                    return newVideo;
                                                });

        AcademyVideoData academyVideoData = academyVideo.getAcademyVideoData();
        OnboardingVideoData onboardingVideoData = onboardingVideo.getOnboardingVideoData();
        onboardingVideoData.setAcademy(academyVideoData.getAcademy());
        onboardingVideoData.setLastUpdated(LocalDateTime.now());
        onboardingVideoData.setRandomPriority(ThreadLocalRandom.current().nextInt(99999));
        onboardingVideoData.setDeleted(false);
        onboardingVideoData.setOwner(owner);
        onboardingVideoData.setPriority(academyVideoData.getPriority());

        oma.update(onboardingVideo);

        return onboardingVideo;
    }

    @Override
    public void markOutdatedOnboardingVideosAsDeleted(String academy, String owner, Set<String> videoIds) {
        for (String videoId : videoIds) {
            oma.find(SQLOnboardingVideo.class, videoId).ifPresent(video -> {
                try {
                    video.getOnboardingVideoData().setLastUpdated(LocalDateTime.now());
                    video.getOnboardingVideoData().setDeleted(true);
                    oma.update(video);
                } catch (Exception e) {
                    Exceptions.handle(Log.BACKGROUND, e);
                }
            });
        }
    }

    @Override
    public void deleteOnboardingVideosFor(String owner) {
        oma.select(SQLOnboardingVideo.class)
           .eq(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.OWNER), owner)
           .delete();
    }

    @Override
    public Optional<? extends OnboardingVideo> fetchSomeVideo(String owner) {
        return oma.select(SQLOnboardingVideo.class)
                  .eq(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.OWNER), owner)
                  .eq(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.RECOMMENDED), true)
                  .orderAsc(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.RANDOM_PRIORITY))
                  .first();
    }

    @Override
    public List<? extends OnboardingVideo> fetchVideos(String owner, String trackId) {
        return oma.select(SQLOnboardingVideo.class)
                  .eq(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.OWNER), owner)
                  .eq(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.DELETED), false)
                  .eq(SQLOnboardingVideo.ACADEMY_VIDEO.join(AcademyVideo.ACADEMY_VIDEO_DATA.inner(AcademyVideoData.TRACK_ID)),
                      trackId)
                  .orderAsc(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.PRIORITY))
                  .queryList();
    }

    @Override
    public List<AcademyTrackInfo> fetchTracks(String target) {
        return Collections.singletonList(new AcademyTrackInfo("track", "Track", 1, 1));
    }

    @Override
    public OnboardingVideo fetchVideo(String owner, String videoId) {
        return oma.select(SQLOnboardingVideo.class)
                  .eq(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.OWNER), owner)
                  .eq(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.DELETED), false)
                  .eq(SQLOnboardingVideo.ID, Long.parseLong(videoId))
                  .queryFirst();
    }

    @Override
    public List<? extends OnboardingVideo> fetchOtherRecommendations(String owner, String videoId) {
        return oma.select(SQLOnboardingVideo.class)
                  .eq(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.OWNER), owner)
                  .ne(SQLOnboardingVideo.ID, Long.parseLong(videoId))
                  .eq(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.RECOMMENDED), true)
                  .orderAsc(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.RANDOM_PRIORITY))
                  .limit(5)
                  .queryList();
    }

    @Override
    public void recordVideoShown(String owner, String videoId) {
        try {
            oma.updateStatement(SQLOnboardingVideo.class)
               .setToNow(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.LAST_SHOWN_IN_UI))
               .inc(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.NUM_SHOWN_IN_UI))
               .where(SQLOnboardingVideo.ID, Long.parseLong(videoId))
               .executeUpdate();
        } catch (NumberFormatException e) {
            Exceptions.ignore(e);
        } catch (SQLException e) {
            Exceptions.handle()
                      .to(Log.BACKGROUND)
                      .error(e)
                      .withSystemErrorMessage(
                              "Failed to record that an onboarding video was offered to the user: %s (%s)")
                      .handle();
        }
    }

    @Override
    public void recordVideoStarted(String owner, String videoId) {
        try {
            oma.updateStatement(SQLOnboardingVideo.class)
               .setToNow(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.LAST_WATCHED))
               .inc(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.NUM_WATCHED))
               .where(SQLOnboardingVideo.ID, Long.parseLong(videoId))
               .executeUpdate();
        } catch (NumberFormatException e) {
            Exceptions.ignore(e);
        } catch (SQLException e) {
            Exceptions.handle()
                      .to(Log.BACKGROUND)
                      .error(e)
                      .withSystemErrorMessage(
                              "Failed to record that playback of an onboarding video was commenced: %s (%s)")
                      .handle();
        }
    }

    @Override
    public void updateWachedPercent(String owner, String videoId, int seenInPercent) {
        try {
            SQLOnboardingVideo video = oma.select(SQLOnboardingVideo.class)
                                          .fields(SQLOnboardingVideo.ID,
                                                  SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.PERCENT_WATCHED))
                                          .eq(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.OWNER),
                                              owner)
                                          .eq(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.DELETED),
                                              false)
                                          .eq(SQLOnboardingVideo.ID, Long.parseLong(videoId))
                                          .queryFirst();

            if (video == null) {
                return;
            }

            if (video.getOnboardingVideoData().getPercentWatched() > seenInPercent) {
                return;
            }

            boolean markAsWatched = seenInPercent >= 40;
            oma.updateStatement(SQLOnboardingVideo.class)
               .set(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.PERCENT_WATCHED), seenInPercent)
               .set(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.SKIPPED), false)
               .set(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.RECOMMENDED), !markAsWatched)
               .set(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.WATCHED), markAsWatched)
               .where(SQLOnboardingVideo.ID, video.getId())
               .executeUpdate();
        } catch (NumberFormatException e) {
            Exceptions.ignore(e);
        } catch (SQLException e) {
            Exceptions.handle()
                      .to(Log.BACKGROUND)
                      .error(e)
                      .withSystemErrorMessage("Failed to record the view progress of an onboarding video: %s (%s)")
                      .handle();
        }
    }

    @Override
    public void markAsSkipped(String owner, String videoId) {
        try {
            oma.updateStatement(SQLOnboardingVideo.class)
               .set(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.RECOMMENDED), false)
               .set(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.SKIPPED), true)
               .where(SQLOnboardingVideo.ID, Long.parseLong(videoId))
               .where(SQLOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.WATCHED), false)
               .executeUpdate();
        } catch (NumberFormatException e) {
            Exceptions.ignore(e);
        } catch (SQLException e) {
            Exceptions.handle()
                      .to(Log.BACKGROUND)
                      .error(e)
                      .withSystemErrorMessage("Failed to record that an onboarding video should be skipped: %s (%s)")
                      .handle();
        }
    }
}
