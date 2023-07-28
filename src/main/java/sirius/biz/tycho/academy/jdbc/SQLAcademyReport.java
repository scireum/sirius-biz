/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.academy.jdbc;

import sirius.biz.analytics.reports.Report;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.tycho.academy.AcademyReport;
import sirius.biz.tycho.academy.AcademyVideoData;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.Row;
import sirius.kernel.commons.Amount;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.List;

/**
 * Provides a usage report for the onboarding / video academy based on the JDBC data.
 */
@Register(framework = SQLOnboardingEngine.FRAMEWORK_TYCHO_JDBC_ACADEMIES)
@Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT_MEMBER)
public class SQLAcademyReport extends AcademyReport {

    @Part
    private OMA oma;

    @Override
    protected void outputVideos(Report report) {
        oma.select(SQLAcademyVideo.class)
           .eq(SQLAcademyVideo.ACADEMY_VIDEO_DATA.inner(AcademyVideoData.DELETED), false)
           .orderDesc(SQLAcademyVideo.ACADEMY_VIDEO_DATA.inner(AcademyVideoData.LAST_UPDATED))
           .iterateAll(academyVideo -> {
               try {
                   Row row = oma.getDatabase(SQLOnboardingVideo.class)
                                .createQuery(//language=SQL
                                             """
                                                     SELECT count(*) AS total,
                                                     sum(onboardingVideoData_watched) AS watched,
                                                     sum(onboardingVideoData_skipped) AS skipped,
                                                     sum(onboardingVideoData_recommended) AS recommended,
                                                     avg(onboardingVideoData_percentWatched) AS percentWatched
                                                     FROM sqlonboardingvideo
                                                     WHERE academyVideo = ${academyVideo}
                                                       AND onboardingVideoData_deleted = 0
                                                     """)
                                .markAsLongRunning()
                                .set("academyVideo", academyVideo.getId())
                                .queryFirst();
                   int watched = row.getValue(COLUMN_WATCHED).asInt(0);
                   int skipped = row.getValue(COLUMN_SKIPPED).asInt(0);
                   int recommended = row.getValue(COLUMN_RECOMMENDED).asInt(0);
                   Amount percentWatched =
                           row.getValue(COLUMN_PERCENT_WATCHED).getAmount().fill(Amount.ZERO).times(Amount.ONE_HUNDRED);

                   report.addRow(List.of(Tuple.create(COLUMN_TRACK,
                                                      cells.of(academyVideo.getAcademyVideoData().getTrackName())),
                                         Tuple.create(COLUMN_VIDEO,
                                                      cells.of(academyVideo.getAcademyVideoData().getVideoCode()
                                                               + " - "
                                                               + Strings.limit(academyVideo.getAcademyVideoData()
                                                                                           .getTitle(), 50, true))),
                                         Tuple.create(COLUMN_WATCHED, cells.of(watched)),
                                         Tuple.create(COLUMN_SKIPPED, cells.of(skipped)),
                                         Tuple.create(COLUMN_RECOMMENDED, cells.of(recommended)),
                                         Tuple.create(COLUMN_PERCENT_WATCHED, cells.of(percentWatched))));
               } catch (SQLException e) {
                   throw Exceptions.handle(Log.APPLICATION, e);
               }
           });
    }

    @Nonnull
    @Override
    public String getName() {
        return "SQLAcademyReport";
    }
}
