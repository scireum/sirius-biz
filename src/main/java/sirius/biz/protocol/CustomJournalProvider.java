/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import java.util.function.Consumer;

/**
 * Permits to add custom info to a {@link JournalEntry}.
 * <p>
 * This can be added to a {@link sirius.db.mixing.BaseEntity} in order to write custom journal info. For example,
 * changes to serialized data, or {@link sirius.db.mixing.Nested} properties can be logged in a more readable manner.
 */
public interface CustomJournalProvider {

    /**
     * Permits to add additional info when writing a {@link JournalEntry}.
     *
     * @param journalConsumer the consumer to supply additional info to
     */
    void addCustomJournal(Consumer<String> journalConsumer);
}
