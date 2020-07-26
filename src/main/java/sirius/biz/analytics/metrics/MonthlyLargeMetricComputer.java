/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics;

import sirius.biz.analytics.scheduler.AnalyticalTask;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.di.std.AutoRegister;
import sirius.kernel.di.std.Part;

import javax.annotation.Nullable;

/**
 * Provides a base class for monthly metric computation for each entity just like {@link MonthlyMetricComputer}.
 * <p>
 * Note however, that this is intended for compute intense tasks, therefore the batch size for this computer
 * is heavily reduced and also no daily best effort computation will take place.
 * <p>
 * Subclasses have to be {@link sirius.kernel.di.std.Register registered} as <tt>MonthlyMetricComputer</tt> so that
 * they are visible to the framework.
 *
 * @param <E> the type of entities being processed by this computer
 */
@AutoRegister
public abstract class MonthlyLargeMetricComputer<E extends BaseEntity<?>> implements AnalyticalTask<E> {

    @Part
    @Nullable
    protected Metrics metrics;
}
