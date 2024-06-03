/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.indicators;

import sirius.biz.analytics.checks.ChangeCheck;
import sirius.biz.protocol.Traced;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.di.PartCollection;
import sirius.kernel.di.std.Parts;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import java.time.LocalDateTime;

/**
 * Provides a change check which executes all {@link Indicator#isBatch() batched} indicators for an entity.
 * <p>
 * A subclass of this has to be created and {@link sirius.kernel.di.std.Register registered} as <tt>ChangeCheck</tt>
 * for each entity for which batched indicators are present.
 *
 * @param <E> the type of entities being processed
 */
public abstract class BatchIndicatorCheck<E extends BaseEntity<?> & IndicatedEntity & Traced> extends ChangeCheck<E> {

    @Parts(Indicator.class)
    private static PartCollection<Indicator<?>> indicators;

    @SuppressWarnings("unchecked")
    @Override
    protected void execute(E entity) {
        indicators.getParts()
                  .stream()
                  .filter(Indicator::isBatch)
                  .filter(indicator -> indicator.getType().isAssignableFrom(entity.getClass()))
                  .forEach(indicator -> {
                      try {
                          entity.getIndicators()
                                .updateIndication(indicator.getName(), ((Indicator<E>) indicator).executeFor(entity));
                      } catch (Exception exception) {
                          Exceptions.handle(Log.BACKGROUND, exception);
                          entity.getIndicators().updateIndication(indicator.getName(), false);
                      }
                  });

        entity.getTrace().setSilent(true);
        entity.getIndicators().setLastBatchIndicatorExecution(LocalDateTime.now());
        entity.getDescriptor().getMapper().update(entity);
    }
}
