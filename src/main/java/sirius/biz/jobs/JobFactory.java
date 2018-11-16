/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import sirius.biz.jobs.params.Parameter;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Named;
import sirius.kernel.di.std.Priorized;
import sirius.web.http.WebContext;
import sirius.web.services.JSONStructuredOutput;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;

/**
 * Represents a job which can be executed either by the user (via the UI), via a HTTP call or by the system itself (e.g. a scheduler).
 * <p>
 * Note that a job (factory) only exists once and has to be stateless.
 * <p>
 * Also note, that an implementation should most probably subclass {@link BasicJobFactory} as it performs all the
 * heavy lifting.
 */
public interface JobFactory extends Named, Priorized {

    /**
     * Returns the label to show for this job.
     *
     * @return the label (translated name) of this job
     */
    String getLabel();

    /**
     * Returns the icon to used when displaying this job.
     *
     * @return the icon of this job
     */
    String getIcon();

    /**
     * Returns a short description of this job.
     *
     * @return the description for this job
     */
    @Nullable
    String getDescription();

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
    List<Parameter<?, ?>> getParameters();

    /**
     * Generates a URL which can be invoked to start this job while using the given object as a parameter value.
     * <p>
     * This is used by the <tt>w:jobs</tt> tag to display appropriate jobs next to a data object.
     *
     * @param targetObject the object to use as a parameter value
     * @return an url which starts the launch screen for this job while using the given parameter as value or
     * <tt>null</tt> to indicate that this jobs cannot be started in the ui or that the given object isn't an
     * appropriate parameter.
     */
    @Nullable
    String generatePresetUrl(Object targetObject);

    /**
     * Determines if this job can be started in the UI.
     *
     * @return <tt>true</tt> if this job can be started in the ui
     */
    boolean canStartInUI();

    /**
     * Starts this job by responding to the given request.
     * <p>
     * Note that the job itself has to enforce its {@link #getRequiredPermissions()}.
     *
     * @param request the request to respond to
     */
    void startInUI(WebContext request);

    /**
     * Determines if this job can be started via a JSON call.
     *
     * @return <tt>true</tt> if this job can be started via a call
     */
    boolean canStartInCall();

    /**
     * Starts this job and returns the result into the given JSON output.
     * <p>
     * Note that the job itself has to enforce its {@link #getRequiredPermissions()}.
     *
     * @param request the request which started the job
     * @param out     the JSON output to populate
     */
    void startInCall(WebContext request, JSONStructuredOutput out);

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
     */
    void startInBackground(Function<String, Value> parameterProvider);

    /**
     * Returns the name of the {@link JobCategory} this job belongs to.
     *
     * @return the name of the job category
     */
    String getCategory();
}
