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
public class SQLOnboardingVideo extends SQLEntity implements OnboardingVideo {

    @Part
    @Nullable
    private static OnboardingEngine onboardingEngine;

    public static final Mapping ACADEMY_VIDEO = Mapping.named("academyVideo");
    private final SQLEntityRef<SQLAcademyVideo> academyVideo =
            SQLEntityRef.on(SQLAcademyVideo.class, BaseEntityRef.OnDelete.IGNORE);

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
}
