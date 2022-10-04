/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import sirius.biz.jobs.params.Autocompleter;
import sirius.biz.web.Action;
import sirius.biz.web.BizController;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.Injector;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.nls.NLS;
import sirius.web.controller.AutocompleteHelper;
import sirius.web.controller.Controller;
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.services.InternalService;
import sirius.web.services.JSONStructuredOutput;
import sirius.web.services.PublicService;

import java.util.List;

/**
 * Provides the UI for the jobs framework.
 */
@Register(framework = Jobs.FRAMEWORK_JOBS)
public class JobsController extends BizController {

    @Part
    private Jobs jobs;

    /**
     * Used to list all available jobs for the current user.
     *
     * @param webContext the current request
     */
    @Routed("/jobs")
    @DefaultRoute
    @LoginRequired
    public void jobs(WebContext webContext) {
        List<Action> actions =
                jobs.getAvailableJobs(null).filter(JobFactory::canStartInteractive).map(this::toAction).toList();
        webContext.respondWith().template("/templates/biz/jobs/jobs.html.pasta", actions);
    }

    private Action toAction(JobFactory jobFactory) {
        return new Action(jobFactory.getLabel(),
                          "/job/" + jobFactory.getName(),
                          NLS.smartGet(jobFactory.getCategory())).withDescription(jobFactory.getDescription())
                                                                 .withIcon(jobFactory.getIcon());
    }

    /**
     * Launches the job with the given name.
     *
     * @param webContext the current request
     * @param jobType    the name of the job to launch
     */
    @Routed("/job/:1")
    @LoginRequired
    public void job(WebContext webContext, String jobType) {
        jobs.findFactory(jobType, JobFactory.class).startInteractively(webContext);
    }

    /**
     * Checks the user input on the job parameters and returns a response that will be handled in the frontend
     * accordingly.
     *
     * @param webContext the web context
     * @param out        the output to write the JSON response to
     * @param jobType    the type of the job, so we can find a suitable job factory
     */
    @Routed("/job/params/:1")
    @InternalService
    @LoginRequired
    public void params(WebContext webContext, JSONStructuredOutput out, String jobType) {
        out.property("params", jobs.findFactory(jobType, JobFactory.class).computeRequiredParameterUpdates(webContext));
    }

    /**
     * Outputs the documentation for a job.
     *
     * @param webContext the current request
     * @param jobType    the name of the job to fetch the documentation for
     */
    @Routed("/jobs/infos/:1")
    @LoginRequired
    public void infos(WebContext webContext, String jobType) {
        webContext.respondWith()
                  .template("/templates/biz/jobs/infos.html.pasta", jobs.findFactory(jobType, JobFactory.class));
    }

    /**
     * Uses a JSON call to invoke a job.
     *
     * @param webContext the current request
     * @param out        the output to write the JSON response to
     * @param jobType    the name of the job to launch
     */
    @Routed("/jobs/api/:1")
    @PublicService(apiName = "jobs-processes", path = "/jobs/api/{job}")
    @Operation(summary = "Start Job", method = "POST", description = """
            Starts the given job. Note that the expected job parameters have to be passed in as additional HTTP
            parameters. Note that only POST requests are handled to prevent forged requests from being executed
            too easily.
            """)
    @ApiResponse(responseCode = "200",
            description = "Successfully started job containing the ID of the started process.",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(//language=JSON
                    """
                            {
                                "success":true,
                                "error":false,
                                "process":"KR8E6I36AK7POAK0IP9L3KB0Q1"
                            }
                            """)))
    @ApiResponse(responseCode = "400",
            description = "Missing or invalid job parameters",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(//language=JSON
                    """
                            {
                                "success":false,
                                "error":true,
                                "message":"The parameter {label} must be filled."
                            }
                            """)))
    @ApiResponse(responseCode = "401",
            description = "Missing authentication",
            content = @Content(mediaType = "application/json"))
    @ApiResponse(responseCode = "404",
            description = "Unknown or inaccessible job",
            content = @Content(mediaType = "application/json"))

    @ApiResponse(responseCode = "405",
            description = "Attempting a GET instead of a POST.",
            content = @Content(mediaType = "application/json"))
    @Parameter(name = "process",
            description = "The name of the job to start.",
            required = true,
            example = "jupiter-sync")
    public void jsonApi(WebContext webContext, JSONStructuredOutput out, String jobType) {
        if (webContext.isUnsafePOST()) {
            try {
                JobFactory factory = jobs.findFactory(jobType, JobFactory.class);
                out.property("process", factory.startInBackground(webContext::get));
            } catch (IllegalArgumentException e) {
                throw Exceptions.createHandled()
                                .withDirectMessage(Strings.apply("Unknown factory: %s", jobType))
                                .hint(Controller.HTTP_STATUS, HttpResponseStatus.NOT_FOUND.code())
                                .handle();
            } catch (HandledException e) {
                throw e.withHint(Controller.HTTP_STATUS, HttpResponseStatus.BAD_REQUEST.code());
            }
        } else {
            throw Exceptions.createHandled()
                            .withDirectMessage("A POST request is expected.")
                            .hint(HTTP_STATUS, HttpResponseStatus.METHOD_NOT_ALLOWED.code())
                            .handle();
        }
    }

    /**
     * A route that can handle autocompletes of parameter input fields via the {@link Autocompleter}.
     *
     * @param webContext        the web context
     * @param out               the output to write the JSON response to
     * @param autocompleterName the name of the autocompleter
     */
    @Routed("/jobs/parameter-autocomplete/:1")
    @InternalService
    @LoginRequired
    public void autocomplete(WebContext webContext, JSONStructuredOutput out, String autocompleterName) {
        Autocompleter<?> autocompleter = Injector.context().getPart(autocompleterName, Autocompleter.class);
        AutocompleteHelper.handle(webContext, (query, result) -> autocompleter.suggest(query, webContext, result));
    }
}
