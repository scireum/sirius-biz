/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.academy.jdbc;

import sirius.biz.tycho.academy.AcademyVideo;
import sirius.biz.tycho.academy.AcademyVideoData;
import sirius.biz.tycho.academy.OnboardingEngine;
import sirius.biz.tycho.academy.OnboardingVideo;
import sirius.biz.tycho.academy.OnboardingVideoData;
import sirius.db.jdbc.SQLEntity;
import sirius.db.jdbc.SQLEntityRef;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Index;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;

import javax.annotation.Nullable;

/**
 * Stores an {@link OnboardingVideoData onboarding video} in a JDBC/SQL database.
 */
@Framework(SQLOnboardingEngine.FRAMEWORK_TYCHO_JDBC_ACADEMIES)
@Index(name = "owner_sync_lookup", columns = {"onboardingVideoData_owner", "onboardingVideoData_syncToken"})
@Index(name = "owner_video_lookup", columns = {"onboardingVideoData_owner", "onboardingVideoData_recommended"})
@Index(name = "owner_videoCode_lookup", columns = {"onboardingVideoData_owner", "videoCode"})
@Index(name = "owner_video_track_lookup",
        columns = {"onboardingVideoData_owner", "onboardingVideoData_deleted", "trackId"})
public class SQLOnboardingVideo extends SQLEntity implements OnboardingVideo {

    @Part
    @Nullable
    private static OnboardingEngine onboardingEngine;

    public static final Mapping ACADEMY_VIDEO = Mapping.named("academyVideo");
    private final SQLEntityRef<SQLAcademyVideo> academyVideo =
            SQLEntityRef.on(SQLAcademyVideo.class, BaseEntityRef.OnDelete.IGNORE);

    /**
     * Contains the track id, this video is part of.
     * <p>
     * This is copied from {@link AcademyVideoData#TRACK_ID} as we require this for filtering and keeping a copy is
     * cheaper than performing a JOIN.
     */
    public static final Mapping TRACK_ID = Mapping.named("trackId");
    @Length(50)
    @NullAllowed
    private String trackId;

    /**
     * Contains the user-defined code of the video.
     * <p>
     * This is copied from {@link AcademyVideoData#VIDEO_CODE} as we require this for filtering and keeping a copy is
     * cheaper than performing a JOIN.
     */
    public static final Mapping VIDEO_CODE = Mapping.named("videoCode");
    @Length(50)
    @NullAllowed
    private String videoCode;

    private final OnboardingVideoData onboardingVideoData = new OnboardingVideoData();

    @Transient
    private AcademyVideoData academyVideoData;

    @Override
    public BaseEntityRef<Long, ? extends AcademyVideo> getAcademyVideo() {
        return academyVideo;
    }

    @Override
    public OnboardingVideoData getOnboardingVideoData() {
        return onboardingVideoData;
    }

    @Override
    public AcademyVideoData fetchAcademyVideoData() {
        if (academyVideoData == null) {
            academyVideoData = onboardingEngine.fetchAcademyVideo(academyVideo.getIdAsString()).getAcademyVideoData();
        }

        return academyVideoData;
    }

    public String getTrackId() {
        return trackId;
    }

    public void setTrackId(String trackId) {
        this.trackId = trackId;
    }

    public String getVideoCode() {
        return videoCode;
    }

    public void setVideoCode(String videoCode) {
        this.videoCode = videoCode;
    }
}
