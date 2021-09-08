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
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Index;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.db.mongo.Mango;
import sirius.db.mongo.MongoEntity;
import sirius.db.mongo.types.MongoRef;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;

import javax.annotation.Nullable;

/**
 * Stores an {@link OnboardingVideoData onboarding video} in MongoDB.
 */
@Framework(MongoOnboardingEngine.FRAMEWORK_TYCHO_MONGO_ACADEMIES)
@Index(name = "owner_sync_lookup",
        columns = {"onboardingVideoData_owner", "onboardingVideoData_syncToken"},
        columnSettings = {Mango.INDEX_ASCENDING, Mango.INDEX_ASCENDING})
@Index(name = "owner_video_lookup",
        columns = {"onboardingVideoData_owner", "onboardingVideoData_recommended"},
        columnSettings = {Mango.INDEX_ASCENDING, Mango.INDEX_ASCENDING})
@Index(name = "owner_video_track_lookup",
        columns = {"onboardingVideoData_owner", "onboardingVideoData_deleted", "trackId"},
        columnSettings = {Mango.INDEX_ASCENDING, Mango.INDEX_ASCENDING, Mango.INDEX_ASCENDING})
public class MongoOnboardingVideo extends MongoEntity implements OnboardingVideo {

    @Part
    @Nullable
    private static OnboardingEngine onboardingEngine;

    public static final Mapping ACADEMY_VIDEO = Mapping.named("academyVideo");
    private final MongoRef<MongoAcademyVideo> academyVideo =
            MongoRef.on(MongoAcademyVideo.class, BaseEntityRef.OnDelete.IGNORE);

    /**
     * Contains the track id, this video is part of.
     * <p>
     * This is copied from {@link AcademyVideoData#TRACK_ID} as we require this for filtering and MongoDB has
     * not efficient joins.
     */
    public static final Mapping TRACK_ID = Mapping.named("trackId");
    @Length(50)
    @NullAllowed
    private String trackId;

    private final OnboardingVideoData onboardingVideoData = new OnboardingVideoData();

    @Transient
    private AcademyVideoData academyVideoData;

    @Override
    public BaseEntityRef<String, ? extends AcademyVideo> getAcademyVideo() {
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
}
