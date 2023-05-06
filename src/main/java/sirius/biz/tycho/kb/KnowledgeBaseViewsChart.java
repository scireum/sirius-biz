/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

import sirius.biz.analytics.events.EventRecorder;
import sirius.biz.analytics.events.PageImpressionEvent;
import sirius.biz.analytics.explorer.ChartFactory;
import sirius.biz.analytics.explorer.ChartObjectResolver;
import sirius.biz.analytics.explorer.EventTimeSeriesComputer;
import sirius.biz.analytics.explorer.TimeSeriesChartFactory;
import sirius.biz.analytics.explorer.TimeSeriesComputer;
import sirius.biz.jobs.StandardCategories;
import sirius.biz.tenants.TenantUserManager;
import sirius.kernel.commons.Callback;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Shows the views of the whole {@link KnowledgeBase} based on recorded
 * {@link sirius.biz.analytics.events.PageImpressionEvent page impression events}.
 */
@Register(framework = KnowledgeBase.FRAMEWORK_KNOWLEDGE_BASE)
@Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT_MEMBER)
public class KnowledgeBaseViewsChart extends TimeSeriesChartFactory<Object> {

    @Part
    private EventRecorder eventRecorder;

    @Nullable
    @Override
    protected Class<? extends ChartObjectResolver<Object>> getResolver() {
        return null;
    }

    @Override
    public String getCategory() {
        return StandardCategories.MONITORING;
    }

    @Override
    protected void collectReferencedCharts(Consumer<Class<? extends ChartFactory<Object>>> referenceChartConsumer) {
        // There are no referenced charts...
    }

    @Override
    protected boolean stackValues(boolean hasComparisonPeriod) {
        return !hasComparisonPeriod;
    }

    @Override
    protected void computers(Object ignoredObject,
                             boolean hasComparisonPeriod,
                             boolean isComparisonPeriod,
                             Callback<TimeSeriesComputer<Object>> executor) throws Exception {
        EventTimeSeriesComputer<Object, PageImpressionEvent> computer =
                new EventTimeSeriesComputer<>(PageImpressionEvent.class,
                                              (ignored, query) -> query.eq(PageImpressionEvent.AGGREGATION_URI,
                                                                           "/kba"));
        if (hasComparisonPeriod) {
            computer.addAggregation(EventTimeSeriesComputer.AGGREGATION_EXPRESSION_COUNT, null);
        } else {
            computer.addAggregation(EventTimeSeriesComputer.AGGREGATION_EXPRESSION_COUNT_LOGGED_IN,
                                    EventTimeSeriesComputer.LABEL_LOGGED_IN);
            computer.addAggregation(EventTimeSeriesComputer.AGGREGATION_EXPRESSION_COUNT_ANONYMOUS,
                                    EventTimeSeriesComputer.LABEL_ANONYMOUS);
        }

        executor.invoke(computer);
    }

    @Nonnull
    @Override
    public String getName() {
        return "GlobalKnowledgeBaseViews";
    }

    @Override
    public int getPriority() {
        return 8200;
    }
}
