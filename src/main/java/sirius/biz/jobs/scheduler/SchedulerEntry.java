/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.scheduler;

import sirius.biz.jobs.JobConfigData;
import sirius.db.mixing.Entity;
import sirius.db.mixing.Mapping;
import sirius.kernel.commons.Explain;

/**
 * Describes entities which are handled by the {@link SchedulerController} as well as the {@link JobSchedulerLoop}.
 */
@SuppressWarnings("squid:S1214")
@Explain("We rather keep the constants here, as this emulates the behaviour and layout of a real entity.")
public interface SchedulerEntry extends Entity {

    /**
     * Contains the scheduler data composite which describes when the job is to be executed.
     */
    Mapping SCHEDULER_DATA = Mapping.named("schedulerData");

    /**
     * Contains the job configuration composite which describes the job to execute along with its parameters.
     */
    Mapping JOB_CONFIG_DATA = Mapping.named("jobConfigData");

    /**
     * Provides access to the schedulder data stored in the underlying entity.
     *
     * @return the scheduler data of this entry.
     */
    SchedulerData getSchedulerData();

    /**
     * Provides access to the job configuration stored in the underlying entity.
     *
     * @return the job configuration of this entry.
     */
    JobConfigData getJobConfigData();

    @Override
    String toString();
}
