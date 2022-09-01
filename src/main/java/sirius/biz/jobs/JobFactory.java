/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import com.alibaba.fastjson.JSON;
import sirius.biz.jobs.infos.JobInfo;
import sirius.biz.jobs.params.Parameter;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.AutoRegister;
import sirius.kernel.di.std.Named;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.health.HandledException;
import sirius.web.http.WebContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Represents a job which can be executed either by the user (via the UI), via a HTTP call or by the system itself (e.g. a scheduler).
 * <p>
 * Note that a job (factory) only exists once and has to be stateless.
 * <p>
 * Also note, that an implementation should most probably subclass {@link BasicJobFactory} as it performs all the
 * heavy lifting.
 */
@AutoRegister
public interface JobFactory extends Named, Priorized {

    /**
     * Returns the label to show for this job.
     *
     * @return the label (translated name) of this job
     */
    String getLabel();

    /**
     * Returns the icon to used when displaying this job.
     * <p>
     * This is actually the css class to use (e.g. to apply a Fontawesome icon).
     *
     * @return the icon of this job
     */
    String getIcon();

    /**
     * Returns a short description of this job.
     * <p>
     * This is shown in lists and should therefore be quite concise.
     *
     * @return the description for this job
     */
    @Nullable
    String getDescription();

    /**
     * Returns a short description of the job which may contain HTML markup.
     * <p>
     * If present, this will replace {@link #getDescription()} when rendering the details
     * page.
     *
     * @return the description for this job which might contain HTML markup
     */
    @Nullable
    String getHTMLDescription();

    /**
     * Returns a detailed description of this job.
     * <p>
     * This description is shown in the documentation page of the job and might be more elaborated.
     *
     * @return the detailed description for this job
     */
    @Nullable
    String getDetailDescription();

    /**
     * Provides a detailed documentation of what the job does, which data it expects etc.
     *
     * @return a list of documentation sections for the job
     */
    List<JobInfo> getJobInfos();

    /**
     * Returns a list of permissions which a user must have in order to run this job.
     *
     * @return the list of required permissions to run this job
     */
    List<String> getRequiredPermissions();

    /**
     * Returns the parameters accepted by this job.
     *
     * @return the list of parameters of this job
     */
    List<Parameter<?>> getParameters();

    /**
     * Determines if there are visible parameters in this job.
     *
     * @param context web context
     * @return <tt>true</tt> if visible parameters found, otherwise <tt>false</tt>
     */
    boolean hasVisibleParameters(Map<String, String> context);

    /**
     * Generates a URL which can be invoked to start this job while using the given object as a parameter value.
     * <p>
     * This is used by the <tt>w:jobs</tt> tag to display appropriate jobs next to a data object.
     *
     * @param uri          the uri of the current page (which contains the <tt>w:jobs</tt> tag
     * @param targetObject the optional target object which is being shown / processed / edited by the page
     * @return an url which starts the launch screen for this job while using the given parameter as value or
     * <tt>null</tt> to indicate that this jobs cannot be started in the ui or that the given object isn't an
     * appropriate parameter.
     */
    @Nullable
    String generatePresetUrl(@Nonnull String uri, @Nullable Object targetObject);

    /**
     * Determines if this job can be started in the UI.
     *
     * @return <tt>true</tt> if this job can be started in the ui
     */
    boolean canStartInteractive();

    /**
     * Starts this job by responding to the given request.
     * <p>
     * Note that the job itself has to enforce its {@link #getRequiredPermissions()}.
     *
     * @param request the request to respond to
     */
    void startInteractively(WebContext request);

    /**
     * Determines if this job can be started in the background.
     *
     * @return <tt>true</tt> if an execution in the background is possible, <tt>false</tt> otherwise
     */
    boolean canStartInBackground();

    /**
     * Starts this job in the background.
     * <p>
     * Note that the job itself has to enforce its {@link #getRequiredPermissions()}.
     *
     * @param parameterProvider the parameters provided to the job
     * @return the id of the {@link sirius.biz.process.Process} which has been started to cover the execution or
     * <tt>null</tt> if no process was used.
     */
    @Nullable
    String startInBackground(Function<String, Value> parameterProvider);

    /**
     * Builds a context using values from the given <tt>parameterProvider</tt> and the
     * {@link #getParameters() parameters} specified by this job.
     *
     * @param parameterProvider         used to provide parameter values
     * @param enforceRequiredParameters determines if required parameters should be enforced
     * @param errorConsumer             will be supplied with detected errors
     * @return all provided parameters wrapped as context
     */
    Map<String, String> buildAndVerifyContext(Function<String, Value> parameterProvider,
                                              boolean enforceRequiredParameters,
                                              BiConsumer<Parameter<?>, HandledException> errorConsumer);

    /**
     * Returns the name of the <tt>category</tt> this job belongs to.
     * <p>
     * Within sirius, one of {@link StandardCategories} should be picked. For products a similar set of constants
     * should probably exist.
     *
     * @return the name of the job category
     */
    String getCategory();

    /**
     * Computes a JSON containing the required update operations for the JavaScript frontend.
     *
     * @param ctx the web context containing the values of all the parameters
     * @return a JSON that can be handled by the JavaScript
     */
    JSON computeRequiredParameterUpdates(WebContext ctx);

    /**
     * Computes a JSON containing the required update operations for the JavaScript frontend.
     *
     * @param ctx the context containing the values of all the parameters
     * @return a JSON that can be handled by the JavaScript
     */
    JSON computeRequiredParameterUpdates(Map<String, String> ctx);
}
