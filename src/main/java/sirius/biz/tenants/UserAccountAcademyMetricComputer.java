/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.analytics.flags.PerformanceFlag;
import sirius.biz.analytics.metrics.MonthlyMetricComputer;
import sirius.biz.tycho.academy.OnboardingVideo;
import sirius.biz.tycho.academy.OnboardingVideoData;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.BaseMapper;
import sirius.db.mixing.query.Query;
import sirius.db.mixing.query.constraints.Constraint;
import sirius.kernel.di.std.ConfigValue;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Computes the education level of each {@link UserAccount}.
 * <p>
 * This will compute the metric <tt>user-education-level</tt> which contains the education level in % and is based
 * on the number of watched videos vs. the total number of available videos.
 * <p>
 * Also, the performance flag <tt>academy-user</tt> is toggled, if the education level is above
 * {@link #minEducationLevel}.
 *
 * @param <E> the generic type of the users being handled
 * @see sirius.biz.tenants.jdbc.SQLUserAccountAcademyMetricComputer
 * @see sirius.biz.tenants.mongo.MongoUserAccountAcademyMetricComputer
 */
public abstract class UserAccountAcademyMetricComputer<E extends BaseEntity<?> & UserAccount<?, ?>>
        extends MonthlyMetricComputer<E> {

    /**
     * Contains the name of the monthly metric which records the education level for every user.
     */
    public static final String METRIC_USER_EDUCATION_LEVEL = "user-education-level";

    /**
     * Contains the minimal required education level for the academy user flag.
     */
    @ConfigValue("analytics.user-accounts.minEducationLevel")
    protected int minEducationLevel;

    @Override
    public void compute(LocalDate date,
                        LocalDateTime startOfPeriod,
                        LocalDateTime endOfPeriod,
                        boolean pastDate,
                        E userAccount) throws Exception {
        long totalVideos = getMapper().select(getType())
                                      .eq(OnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.OWNER),
                                          userAccount.getUniqueName())
                                      .eq(OnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.DELETED),
                                          false)
                                      .count();

        if (totalVideos > 0) {
            long watchedVideos = getMapper().select(getType())
                                            .eq(OnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.OWNER),
                                                userAccount.getUniqueName())
                                            .eq(OnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.DELETED),
                                                false)
                                            .where(getMapper().filters()
                                                              .or(getMapper().filters()
                                                                             .eq(OnboardingVideo.ONBOARDING_VIDEO_DATA.inner(
                                                                                         OnboardingVideoData.WATCHED),
                                                                                 true),
                                                                  getMapper().filters()
                                                                             .eq(OnboardingVideo.ONBOARDING_VIDEO_DATA.inner(
                                                                                         OnboardingVideoData.SKIPPED),
                                                                                 true)))
                                            .count();

            int educationLevel = (int) (watchedVideos * 100 / totalVideos);
            metrics.updateMonthlyMetric(userAccount, METRIC_USER_EDUCATION_LEVEL, date, educationLevel);

            if (!pastDate) {
                userAccount.getPerformanceData()
                           .modify()
                           .set(getAcademyUserFlag(), educationLevel >= minEducationLevel)
                           .commit();
            }
        }
    }

    protected abstract PerformanceFlag getAcademyUserFlag();

    protected abstract <B extends BaseEntity<?>, C extends Constraint, Q extends Query<Q, B, C>> BaseMapper<B, C, Q> getMapper();
}
