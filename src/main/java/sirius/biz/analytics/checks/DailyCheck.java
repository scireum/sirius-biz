/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.checks;

import sirius.biz.analytics.scheduler.AnalyticalTask;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.di.std.AutoRegister;

import java.time.LocalDate;

/**
 * Provides a check which is executed on a daily basis per entity.
 * <p>
 * Subclasses must be {@link sirius.kernel.di.std.Register registered} as <b>DailyCheck</b> to make them visible to
 * the framework.
 * <p>
 * If this check will hit a large number of entities, consider using a {@link ChangeCheck} which is only invoked
 * for entities that changed in the last 24h.
 *
 * @param <E> the type of entities being processed by this check.
 */
@AutoRegister
public abstract class DailyCheck<E extends BaseEntity<?>> implements AnalyticalTask<E> {

    @Override
    public void compute(LocalDate date, E entity) {
        execute(entity);
    }

    /**
     * Executes the check on the given entity.
     *
     * @param entity the entity to check
     */
    protected abstract void execute(E entity);
}
