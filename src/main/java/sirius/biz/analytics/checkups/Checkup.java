/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.checkups;

import sirius.biz.analytics.checks.DailyCheck;
import sirius.biz.analytics.flags.ExecutionFlags;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.di.std.Part;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

public abstract class Checkup<E extends BaseEntity<?>> extends DailyCheck<E> {

    private static final Period SAFETY_MARGIN = Period.ofMonths(1);
    private static final String EXECUTION_FLAG_PREFIX = "checkup-";

    @Part
    private ExecutionFlags flags;

    protected abstract String getName();

    protected abstract Period getInterval();

    protected Period getStorageInterval() {
        return Period.ofDays(getInterval().getDays() + SAFETY_MARGIN.getDays());
    }

    protected boolean shouldExecute(LocalDateTime lastExecution) {
        return lastExecution == null
               || Period.between(lastExecution.toLocalDate(), LocalDate.now()).getDays() >= getInterval().getDays();
    }

    @Override
    protected void execute(E entity) {
        String flagName = EXECUTION_FLAG_PREFIX + getName();
        LocalDateTime lastExecution = flags.readExecutionFlag(entity, flagName).orElse(null);
        if (shouldExecute(lastExecution)) {
            performCheckup(entity);
            flags.storeExecutionFlag(entity, flagName, LocalDateTime.now(), getStorageInterval());
        }
    }

    protected abstract void performCheckup(E entity);
}
