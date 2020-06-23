/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.checks;

import sirius.biz.analytics.scheduler.AnalyticalTask;
import sirius.biz.protocol.Traced;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.di.std.AutoRegister;

import java.time.LocalDate;

/**
 * Provides a check which is executed once an entity has been changed.
 * <p>
 * Note that like {@link DailyCheck} this will also only be executed once per day but only matches entitis which have
 * been modified within the last 24 hours.
 * <p>
 * Subclasses must be {@link sirius.kernel.di.std.Register registered} as <b>ChangeCheck</b> to make them visible to
 * the framework.
 *
 * @param <E> the type of entities being processed by this check.
 */
@AutoRegister
public abstract class ChangeCheck<E extends BaseEntity<?> & Traced> implements AnalyticalTask<E> {

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
