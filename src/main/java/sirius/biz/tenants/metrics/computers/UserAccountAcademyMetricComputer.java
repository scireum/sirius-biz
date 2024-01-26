/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.metrics.computers;

import sirius.biz.analytics.flags.PerformanceFlag;
import sirius.biz.analytics.metrics.MetricComputerContext;
import sirius.biz.analytics.metrics.MonthlyMetricComputer;
import sirius.biz.tenants.UserAccount;
import sirius.biz.tycho.academy.OnboardingVideo;
import sirius.biz.tycho.academy.OnboardingVideoData;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.BaseMapper;
import sirius.db.mixing.query.Query;
import sirius.db.mixing.query.constraints.Constraint;
import sirius.kernel.di.std.ConfigValue;

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
     * Defines the age (in days) for an onboarding video to be picked up by the statistics. If we'd also take newer
     * videos into account, the user had no chance to actually view them and thus the education level and the
     * related performance flag would fluctuate heavily.
     */
    private static final int MIN_AGE_FOR_RELEVANT_VIDEOS_IN_DAYS = 30;

    /**
     * Contains the minimal required education level for the academy user flag.
     */
    @ConfigValue("analytics.user-accounts.minEducationLevel")
    protected int minEducationLevel;

    @Override
    public void compute(MetricComputerContext context, E userAccount) throws Exception {
        if (!context.periodOutsideOfCurrentInterest()) {
            return;
        }
        long totalVideos = queryEligibleOnboardingVideos(userAccount).count();

        if (totalVideos == 0) {
            return;
        }

        Query<? extends Query<?, ?, ?>, ?, Constraint> watchedVideosQuery = queryEligibleOnboardingVideos(userAccount);
        watchedVideosQuery.where(getMapper().filters()
                                            .or(getMapper().filters()
                                                           .eq(OnboardingVideo.ONBOARDING_VIDEO_DATA.inner(
                                                                   OnboardingVideoData.WATCHED), true),
                                                getMapper().filters()
                                                           .eq(OnboardingVideo.ONBOARDING_VIDEO_DATA.inner(
                                                                   OnboardingVideoData.SKIPPED), true)));
        long watchedVideos = watchedVideosQuery.count();

        int educationLevel = (int) (watchedVideos * 100 / totalVideos);
        metrics.updateMonthlyMetric(userAccount, METRIC_USER_EDUCATION_LEVEL, context.date(), educationLevel);

        userAccount.getPerformanceData()
                   .modify()
                   .set(getAcademyUserFlag(), educationLevel >= minEducationLevel)
                   .commit();
    }

    /**
     * Queries visible (non-deleted) onboarding videos for the given user.
     * <p>
     * Note that we filter on videos which have a certain age (at least 30 days), so that the user had a chance to actually
     * watch the video. Otherwise, the statistics would fluctuate with the release of each new video.
     *
     * @param userAccount the user account to fetch videos for
     * @return a query for all relevant {@link OnboardingVideo onboarding videos} for the given user
     */
    private Query<? extends Query<?, ?, ?>, ?, Constraint> queryEligibleOnboardingVideos(E userAccount) {
        return getMapper().select(getOnboardingVideoEntity())
                          .eq(OnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.OWNER),
                              userAccount.getUniqueName())
                          .eq(OnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.DELETED), false)
                          .where(getMapper().filters()
                                            .lt(OnboardingVideo.ONBOARDING_VIDEO_DATA.inner(OnboardingVideoData.CREATED),
                                                LocalDateTime.now().minusDays(MIN_AGE_FOR_RELEVANT_VIDEOS_IN_DAYS)));
    }

    protected abstract Class<? extends BaseEntity<?>> getOnboardingVideoEntity();

    protected abstract PerformanceFlag getAcademyUserFlag();

    protected abstract <B extends BaseEntity<?>, C extends Constraint, Q extends Query<Q, B, C>> BaseMapper<B, C, Q> getMapper();
}
