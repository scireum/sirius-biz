/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

import sirius.biz.analytics.charts.explorer.UserAgentsBrowserDistributionTimeSeriesChartFactory;
import sirius.biz.tenants.TenantUserManager;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;

/**
 * Implements a time series chart for the knowledge base, showing the browser distribution.
 * <p></p>
 * The chart visualizes the browsers which have been used in order to access the knowledge base by extracting the required data from the user agents dataset.
 */
@Register(framework = KnowledgeBase.FRAMEWORK_KNOWLEDGE_BASE)
@Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT_MEMBER)
public class KnowledgeBaseUserAgentsBrowserDistributionChart extends UserAgentsBrowserDistributionTimeSeriesChartFactory {
    @Nonnull
    @Override
    public String getName() {
        return "GlobalKnowledgeBaseUserAgentsBrowserDistributionChart";
    }

    @Override
    public int getPriority() {
        return 900;
    }

    @Override
    protected String getEventName() {
        return "pageimpressionevent";
    }

    @Override
    protected String getConditions() {
        return "aggregationUri = '/kba'";
    }
}