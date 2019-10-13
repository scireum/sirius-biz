/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.presets;

import sirius.biz.jobs.JobFactory;

import java.util.List;

/**
 * Provides a small helper to get hold of all available presets for the current tenant.
 */
public interface JobPresets {

    /**
     * Lists all presets which are available for the given job factory.
     *
     * @param factory the job factory to fetch presets for
     * @return the known presets for the current tenant and the given job factory
     */
    List<? extends JobPreset> fetchPresets(JobFactory factory);
}
