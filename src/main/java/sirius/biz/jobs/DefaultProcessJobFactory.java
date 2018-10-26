/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import sirius.biz.cluster.work.DistributedTaskExecutor;
import sirius.web.security.UserContext;

public abstract class DefaultProcessJobFactory extends BasicProcessJobFactory {

    @Override
    protected String getPenaltyToken() {
        return UserContext.getCurrentUser().getTenantId();
    }

    @Override
    protected Class<? extends DistributedTaskExecutor> getExecutor() {
        return DefaultProcessJobTaskExecutor.class;
    }

    @Override
    protected boolean isPrioritized() {
        return true;
    }
}
