/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import sirius.biz.web.BizController;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Page;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.services.InternalService;
import sirius.web.services.JSONStructuredOutput;

import java.util.Collection;

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
     * @param ctx the current request
     */
    @Routed("/jobs")
    @DefaultRoute
    @LoginRequired
    public void jobs(WebContext ctx) {
        Page<Tuple<JobCategory, Collection<JobFactory>>> page = new Page<>();
        page.bindToRequest(ctx);
        page.withItems(jobs.groupByCategory(jobs.getAvailableJobs(page.getQuery())
                                                .filter(JobFactory::canStartInteractive)));

        ctx.respondWith().template("/templates/biz/jobs/jobs.html.pasta", page);
    }

    /**
     * Launches the job with the given name.
     *
     * @param ctx     the current request
     * @param jobType the name of the job to launch
     */
    @Routed("/job/:1")
    @LoginRequired
    public void job(WebContext ctx, String jobType) {
        jobs.findFactory(jobType, JobFactory.class).startInteractively(ctx);
    }

    /**
     * Outputs the documentation for a job.
     *
     * @param ctx     the current request
     * @param jobType the name of the job to fetch the documentation for
     */
    @Routed("/jobs/infos/:1")
    @LoginRequired
    public void infos(WebContext ctx, String jobType) {
        ctx.respondWith().template("/templates/biz/jobs/infos.html.pasta", jobs.findFactory(jobType, JobFactory.class));
    }

    /**
     * Uses a JSON call to invoke a job.
     *
     * @param ctx     the current request
     * @param out     the output to write the JSON response to
     * @param jobType the name of the job to launch
     */
    @Routed("/jobs/api/:1")
    @InternalService
    public void json(WebContext ctx, JSONStructuredOutput out, String jobType) {
        jobs.findFactory(jobType, JobFactory.class).startInBackground(ctx::get);
    }
}
