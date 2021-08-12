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
public class MongoOnboardingVideo extends MongoEntity implements OnboardingVideo {

    @Part
    @Nullable
    private static OnboardingEngine onboardingEngine;

    public static final Mapping ACADEMY_VIDEO = Mapping.named("academyVideo");
    private final MongoRef<MongoAcademyVideo> academyVideo =
            MongoRef.on(MongoAcademyVideo.class, BaseEntityRef.OnDelete.IGNORE);

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
}
