/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.academy.mongo;

import sirius.biz.analytics.reports.Report;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.tycho.academy.AcademyReport;
import sirius.biz.tycho.academy.AcademyVideoData;
import sirius.biz.tycho.academy.OnboardingVideoData;
import sirius.db.mongo.Mango;
import sirius.db.mongo.facets.MongoAverageFacet;
import sirius.db.mongo.facets.MongoBooleanFacet;
import sirius.kernel.commons.Amount;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Provides a usage report for the onboarding / video academy based on the MongoDB data.
 */
@Register(framework = MongoOnboardingEngine.FRAMEWORK_TYCHO_MONGO_ACADEMIES)
@Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT_MEMBER)
public class MongoAcademyReport extends AcademyReport {

    @Part
    private Mango mango;

    @Override
    protected void outputVideos(Report report) {
        mango.select(MongoAcademyVideo.class)
             .eq(MongoAcademyVideo.ACADEMY_VIDEO_DATA.inner(AcademyVideoData.DELETED), false)
             .orderDesc(MongoAcademyVideo.ACADEMY_VIDEO_DATA.inner(AcademyVideoData.LAST_UPDATED))
             .iterateAll(academyVideo -> {
                 MongoBooleanFacet watchedFacet =
                         new MongoBooleanFacet(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.WATCHED));
                 MongoBooleanFacet skippedFacet =
                         new MongoBooleanFacet(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.SKIPPED));
                 MongoBooleanFacet recommendedFacet =
                         new MongoBooleanFacet(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.SKIPPED));
                 MongoAverageFacet percentWatchedFacet =
                         new MongoAverageFacet(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.PERCENT_WATCHED));
                 mango.select(MongoOnboardingVideo.class)
                      .eq(MongoOnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.DELETED), false)
                      .eq(MongoOnboardingVideo.ACADEMY_VIDEO, academyVideo)
                      .addFacet(watchedFacet)
                      .addFacet(skippedFacet)
                      .addFacet(recommendedFacet)
                      .addFacet(percentWatchedFacet)
                      .executeFacets();

                 report.addRow(List.of(Tuple.create(COLUMN_TRACK,
                                                    cells.of(academyVideo.getAcademyVideoData().getTrackName())),
                                       Tuple.create(COLUMN_VIDEO,
                                                    cells.of(academyVideo.getAcademyVideoData().getVideoCode()
                                                             + " - "
                                                             + Strings.limit(academyVideo.getAcademyVideoData()
                                                                                         .getTitle(), 50, true))),
                                       Tuple.create(COLUMN_WATCHED, cells.of(watchedFacet.getNumTrue())),
                                       Tuple.create(COLUMN_SKIPPED, cells.of(skippedFacet.getNumTrue())),
                                       Tuple.create(COLUMN_RECOMMENDED, cells.of(recommendedFacet.getNumTrue())),
                                       Tuple.create(COLUMN_PERCENT_WATCHED,
                                                    cells.of(percentWatchedFacet.getValue()
                                                                                .fill(Amount.ZERO)
                                                                                .times(Amount.ONE_HUNDRED)))));
             });
    }

    @Nonnull
    @Override
    public String getName() {
        return "MongoAcademyReport";
    }
}
