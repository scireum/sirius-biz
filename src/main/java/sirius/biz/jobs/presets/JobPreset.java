/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.presets;

import sirius.biz.jobs.JobConfigData;
import sirius.biz.protocol.Journaled;
import sirius.biz.web.TenantAware;
import sirius.db.mixing.Mapping;
import sirius.kernel.commons.Explain;

/**
 * Stores a set of job parameters for a tenant.
 */
@SuppressWarnings("squid:S1214")
@Explain("We rather keep the constants here, as this emulates the behaviour and layout of a real entity.")
public interface JobPreset extends TenantAware, Journaled {

    /**
     * Contains the job configuration composite which describes the job to execute along with its parameters.
     */
    Mapping JOB_CONFIG_DATA = Mapping.named("jobConfigData");

    /**
     * Provides access to the job configuration stored in the underlying entity.
     *
     * @return the job configuration of this entry.
     */
    JobConfigData getJobConfigData();

    @Override
    String toString();
}
