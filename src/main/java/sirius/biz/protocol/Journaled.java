/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

/**
 * Marks entities which contain a {@link JournalData} so that other frameworks can contribute to their journal.
 */
public interface Journaled {

    /**
     * Returns the journal of this entity.
     *
     * @return the journal of this entity
     */
    JournalData getJournal();
}
