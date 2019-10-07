/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.presets;

import com.google.common.collect.ImmutableSet;
import sirius.biz.jobs.scheduler.JobConfigData;
import sirius.biz.web.BizController;
import sirius.biz.web.TenantAware;
import sirius.db.mixing.BaseEntity;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.services.JSONStructuredOutput;

/**
 * Provides the database independent part for the controller which is responsible for managing job presets.
 *
 * @param <J> the generic entity type on which the actual controller will operate on
 */
public abstract class JobPresetsController<J extends BaseEntity<?> & JobPreset> extends BizController {

    private static final String PARAM_JOB_FACTORY = "jobFactory";
    private static final String PARAM_PRESET = "preset";
    private static final String PARAM_PRESET_NAME = "presetName";

    private static final ImmutableSet<String> IGNORED_PARAMETERS =
            ImmutableSet.of(PARAM_JOB_FACTORY, PARAM_PRESET_NAME, "CSRFToken", "updateOnly");

    private static final String RESPONSE_PARAMS = "params";
    private static final String RESPONSE_PARAM = "param";
    private static final String RESPONSE_NAME = "name";
    private static final String RESPONSE_VALUE = "value";

    /**
     * Returns the entity class being used by this controller.
     *
     * @return the entity class being used by this controller.
     */
    protected abstract Class<J> getPresetType();

    /**
     * Updates or creates the preset for the submitted job and name.
     * <p>
     * This will create or update the job preset with the given name (and job factory) for the current tenant.
     *
     * @param ctx the request to handle
     * @param out the JSON response to populate (in this case no actual output is expected)
     * @throws Exception in case of an error when populating the entity
     */
    @SuppressWarnings("unchecked")
    @Routed(value = "/jobs/preset/create", jsonCall = true)
    public void create(WebContext ctx, JSONStructuredOutput out) throws Exception {
        J preset = (J) mixing.getDescriptor(getPresetType())
                             .getMapper()
                             .select(getPresetType())
                             .eq(TenantAware.TENANT, tenants.getRequiredTenant())
                             .eq(JobPreset.JOB_CONFIG_DATA.inner(JobConfigData.JOB),
                                 ctx.get(PARAM_JOB_FACTORY).asString())
                             .eq(JobPreset.JOB_CONFIG_DATA.inner(JobConfigData.LABEL),
                                 ctx.get(PARAM_PRESET_NAME).asString())
                             .queryFirst();
        if (preset == null) {
            preset = getPresetType().newInstance();
            preset.getJobConfigData().setJob(ctx.get(PARAM_JOB_FACTORY).asString());
            preset.getJobConfigData().setLabel(ctx.get(PARAM_PRESET_NAME).asString());
            preset.fillWithCurrentTenant();
        } else {
            preset.getJobConfigData().getConfigMap().clear();
        }

        for (String parameter : ctx.getParameterNames()) {
            if (!IGNORED_PARAMETERS.contains(parameter)) {
                preset.getJobConfigData().getConfigMap().put(parameter, ctx.get(parameter).asString());
            }
        }

        mixing.getDescriptor(getPresetType()).getMapper().update(preset);
    }

    /**
     * Outputs the stored configuration (parameters) for the requested job preset.
     *
     * @param ctx the request to handle
     * @param out the JSON response to populate
     */
    @Routed(value = "/jobs/preset/load", jsonCall = true)
    public void load(WebContext ctx, JSONStructuredOutput out) {
        out.beginArray(RESPONSE_PARAMS);
        J preset = mixing.getDescriptor(getPresetType())
                         .getMapper()
                         .find(getPresetType(), ctx.get(PARAM_PRESET).asString())
                         .orElse(null);
        if (preset != null) {
            assertTenant(preset);
            preset.getJobConfigData().getConfigMap().forEach((name, value) -> {
                out.beginObject(RESPONSE_PARAM);
                out.property(RESPONSE_NAME, name);
                out.property(RESPONSE_VALUE, value);
                out.endObject();
            });
        }
        out.endArray();
    }

    /**
     * Deletes the requested job preset.
     *
     * @param ctx the request to handle
     * @param out the JSON response to populate (in this case no actual output is expected)
     */
    @Routed(value = "/jobs/preset/delete", jsonCall = true)
    public void delete(WebContext ctx, JSONStructuredOutput out) {
        if (ctx.isSafePOST()) {
            J preset = mixing.getDescriptor(getPresetType())
                             .getMapper()
                             .find(getPresetType(), ctx.get(PARAM_PRESET).asString())
                             .orElse(null);
            if (preset != null) {
                assertTenant(preset);
                preset.getMapper().delete(preset);
            }
        }
    }
}
