/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.indicators;

import sirius.biz.protocol.NoJournal;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Composite;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.types.StringList;
import sirius.kernel.di.PartCollection;
import sirius.kernel.di.std.Parts;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Stores the data required to execute and query {@link Indicator indicators} / indications for entities.
 */
public class IndicatorData extends Composite {

    @Parts(Indicator.class)
    private static PartCollection<Indicator<?>> indicators;

    @Transient
    protected BaseEntity<?> owner;

    @NoJournal
    private final StringList indications = new StringList();

    @NoJournal
    @NullAllowed
    private LocalDateTime lastBatchIndicatorExecution;

    /**
     * Creates a new instance of the given owner.
     *
     * @param owner the entity for which the composite was created
     */
    public IndicatorData(BaseEntity<?> owner) {
        this.owner = owner;
    }

    /**
     * Sets or clears the given indicator / indication.
     *
     * @param indicator the indicator to set
     * @param newState  <tt>true</tt> to set the indicator, <tt>false</tt> to clear it
     * @return <tt>true</tt> if the indicator was not yet present
     */
    public boolean updateIndication(String indicator, boolean newState) {
        if (newState) {
            return !indications.contains(indicator) && indications.modify().add(indicator);
        } else {
            return indications.contains(indicator) && indications.modify().remove(indicator);
        }
    }

    @BeforeSave
    protected void beforeSave() {
        for (Indicator<?> indicator : indicators) {
            try {
                executeIndicator(indicator);
            } catch (Exception exception) {
                Exceptions.handle(Log.BACKGROUND, exception);
                updateIndication(indicator.getName(), false);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <E extends BaseEntity<?> & IndicatedEntity> void executeIndicator(Indicator<E> indicator) {
        if (indicator.getType().isAssignableFrom(owner.getClass()) && !indicator.isBatch()) {
            updateIndication(indicator.getName(), indicator.executeFor((E) owner));
        }
    }

    /**
     * Returns a list of all indications being present for the current entity.
     *
     * @return a list of all indications being present
     */
    public List<String> getIndications() {
        return indications.original();
    }

    /**
     * Returns the timestamp of the last batch run which processed batch indicators for this entity.
     *
     * @return the timestamp of the last batch run
     */
    public LocalDateTime getLastBatchIndicatorExecution() {
        return lastBatchIndicatorExecution;
    }

    /**
     * Stores the timestamp of the last batch run which processed batch indicators for this entity.
     *
     * @param lastBatchIndicatorExecution the timestamp of the last batch run
     */
    protected void setLastBatchIndicatorExecution(LocalDateTime lastBatchIndicatorExecution) {
        this.lastBatchIndicatorExecution = lastBatchIndicatorExecution;
    }
}
