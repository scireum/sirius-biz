/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.presets;

import sirius.biz.jobs.JobConfigData;
import sirius.biz.web.TenantAware;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Mapping;

/**
 * Stores a set of job parameters for a tenant.
 */
public interface JobPreset extends TenantAware {

    /**
     * Contains the job configuration composite which describes the job to execute along with its parameters.
     */
    Mapping JOB_CONFIG_DATA = Mapping.named("jobConfigData");

    /**
     * Makes {@link BaseEntity#getIdAsString()} visible so that it can be used in Tagliatelle templates.
     *
     * @return the id of the underlying entity as string
     */
    String getIdAsString();

    /**
     * Makes {@link BaseEntity#getUniqueName()} visible so that it can be used in Tagliatelle templates.
     *
     * @return the unique name of this entity
     */
    String getUniqueName();

    /**
     * Makes {@link BaseEntity#isNew()} visible so that it can be used in Tagliatelle templates.
     *
     * @return <tt>ture</tt> if the underlying entity has not been persisted yet, <tt>false</tt> otherwise
     */
    boolean isNew();

    /**
     * Provides access to the job configuration stored in the underlying entity.
     *
     * @return the job configuration of this entry.
     */
    JobConfigData getJobConfigData();

    @Override
    String toString();
}
