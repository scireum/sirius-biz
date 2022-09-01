/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.analytics.flags.PerformanceFlag;
import sirius.biz.tenants.metrics.UserAccountAcademyMetricComputer;
import sirius.biz.tycho.academy.mongo.MongoOnboardingEngine;
import sirius.biz.tycho.academy.mongo.MongoOnboardingVideo;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.BaseMapper;
import sirius.db.mixing.query.Query;
import sirius.db.mixing.query.constraints.Constraint;
import sirius.db.mongo.Mango;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

/**
 * Computes the education level of each {@link MongoUserAccount} if {@link MongoOnboardingEngine} is enabled.
 */
@Register(framework = MongoOnboardingEngine.FRAMEWORK_TYCHO_MONGO_ACADEMIES)
public class MongoUserAccountAcademyMetricComputer extends UserAccountAcademyMetricComputer<MongoUserAccount> {

    /**
     * Contains the performance flag toggled for each user which has an education level above
     * {@link #minEducationLevel}.
     */
    public static final PerformanceFlag VIDEO_ACADEMY_USER =
            PerformanceFlag.register(MongoUserAccount.class, "academy-user", 2).makeVisible().markAsFilter();

    @Override
    public Class<MongoUserAccount> getType() {
        return MongoUserAccount.class;
    }

    @Override
    protected Class<? extends BaseEntity<?>> getOnboardingVideoEntity() {
        return MongoOnboardingVideo.class;
    }

    @Part
    private Mango mango;

    @Override
    protected PerformanceFlag getAcademyUserFlag() {
        return VIDEO_ACADEMY_USER;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <B extends BaseEntity<?>, C extends Constraint, Q extends Query<Q, B, C>> BaseMapper<B, C, Q> getMapper() {
        return (BaseMapper<B, C, Q>) mango;
    }
}
