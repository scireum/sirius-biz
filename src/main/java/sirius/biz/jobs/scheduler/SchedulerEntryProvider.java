/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.scheduler;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Describes a provider of {@link SchedulerEntry scheduler entries} to be handled by the {@link JobSchedulerLoop}.
 *
 * @param <J> the generic parameter of entities being managed by this provider
 */
public interface SchedulerEntryProvider<J extends SchedulerEntry> {

    /**
     * Returns the list of all actively scheduled tasks.
     *
     * @return a list of all actively scheduled tasks
     */
    List<J> getActiveScheduledJobs();

    /**
     * Returns a fully populated entity.
     * <p>
     * As {@link #getActiveScheduledJobs()} might return shallow entities which have only their scheduler fields
     * populated, this method can be used to fetch all information - especially the parameter block.
     *
     * @param job a shallow entity returned by {@link #getActiveScheduledJobs()}
     * @return a fully populated entity
     */
    J fetchFullInformation(J job);

    /**
     * Marks the given task as executed at the given timestamp.
     *
     * @param job       the task to mark as executed
     * @param timestamp the timestamp when the execution was scheduled
     */
    void markExecuted(J job, LocalDateTime timestamp);
}
