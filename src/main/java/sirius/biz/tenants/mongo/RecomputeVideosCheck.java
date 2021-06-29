/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.tenants.UserAccountVideosCheck;
import sirius.kernel.di.std.Register;

/**
 * Computes onboarding videos for all {@link MongoUserAccount users}.
 */
@Register(framework = MongoTenants.FRAMEWORK_TENANTS_MONGO)
public class RecomputeVideosCheck extends UserAccountVideosCheck<String, MongoTenant, MongoUserAccount> {

    @Override
    public Class<MongoUserAccount> getType() {
        return MongoUserAccount.class;
    }
}
