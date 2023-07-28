/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.analytics.flags.PerformanceFlag;
import sirius.biz.tenants.metrics.computers.UserAccountAcademyMetricComputer;
import sirius.biz.tycho.academy.jdbc.SQLOnboardingEngine;
import sirius.biz.tycho.academy.jdbc.SQLOnboardingVideo;
import sirius.db.jdbc.OMA;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.BaseMapper;
import sirius.db.mixing.query.Query;
import sirius.db.mixing.query.constraints.Constraint;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

/**
 * Computes the education level of each {@link SQLUserAccount} if {@link SQLOnboardingEngine} is enabled.
 */
@Register(framework = SQLOnboardingEngine.FRAMEWORK_TYCHO_JDBC_ACADEMIES)
public class SQLUserAccountAcademyMetricComputer extends UserAccountAcademyMetricComputer<SQLUserAccount> {

    /**
     * Contains the performance flag toggled for each user which has an education level above
     * {@link #minEducationLevel}.
     */
    public static final PerformanceFlag VIDEO_ACADEMY_USER =
            PerformanceFlag.register(SQLUserAccount.class, "academy-user", 2).makeVisible().markAsFilter();

    @Override
    public Class<SQLUserAccount> getType() {
        return SQLUserAccount.class;
    }

    @Override
    protected Class<? extends BaseEntity<?>> getOnboardingVideoEntity() {
        return SQLOnboardingVideo.class;
    }

    @Part
    private OMA oma;

    @Override
    protected PerformanceFlag getAcademyUserFlag() {
        return VIDEO_ACADEMY_USER;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <B extends BaseEntity<?>, C extends Constraint, Q extends Query<Q, B, C>> BaseMapper<B, C, Q> getMapper() {
        return (BaseMapper<B, C, Q>) oma;
    }
}
