/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import sirius.db.mixing.Mapping;
import sirius.kernel.commons.Explain;

/**
 * Marks entities which contain a {@link JournalData} so that other frameworks can contribute to their journal.
 */
@SuppressWarnings("squid:S1214")
@Explain("The constant is best kept here for consistency reasons.")
public interface Journaled {

    /**
     * Provides the default mapping for accessing the journal data.
     */
    Mapping JOURNAL = Mapping.named("journal");

    /**
     * Returns the journal of this entity.
     *
     * @return the journal of this entity
     */
    JournalData getJournal();
}
