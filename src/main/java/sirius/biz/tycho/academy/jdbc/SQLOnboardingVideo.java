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
import sirius.db.mixing.types.BaseEntityRef;
import sirius.kernel.di.std.Part;

public class SQLOnboardingVideo extends SQLEntity implements OnboardingVideo {

    @Part
    private static OnboardingEngine onboardingEngine;

    public static final Mapping ACADEMY_VIDEO = Mapping.named("academyVideo");
    private final SQLEntityRef<SQLAcademyVideo> academyVideo =
            SQLEntityRef.on(SQLAcademyVideo.class, BaseEntityRef.OnDelete.REJECT);

    private final OnboardingVideoData onboardingVideoData = new OnboardingVideoData();

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
        return onboardingEngine.fetchAcademyVideo(academyVideo.getIdAsString()).getAcademyVideoData();
    }
}
