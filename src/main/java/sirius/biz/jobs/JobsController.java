/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import sirius.biz.web.BizController;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Controller;
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Message;
import sirius.web.controller.Page;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;

@Register(classes = Controller.class, framework = "") //TODO "jobs"
public class JobsController extends BizController {

    @Part
    private Jobs jobs;

    @Routed("/jobs")
    @DefaultRoute
    public void jobs(WebContext ctx) {
        Page<JobFactory> page = new Page<>();
        page.bindToRequest(ctx);
        page.withItems(jobs.getAvailableJobs(page.getQuery()));

        ctx.respondWith().template("/templates/jobs/jobs.html.pasta", page);
    }

    @Routed("/job/:1")
    public void job(WebContext ctx, String jobType) {
        if (ctx.isSafePOST()) {
            String redirectUri = jobs.execute(jobType, ctx::get);
            if (Strings.isFilled(redirectUri)) {
                ctx.respondWith().redirectToGet(redirectUri);
            } else {
                UserContext.message(Message.info("Dud"));
                ctx.respondWith().redirectToGet("/jobs");
            }

            return;
        }
        ctx.respondWith().template("/templates/jobs/job.html.pasta", jobs.findFactory(jobType, JobFactory.class));
    }

    @Routed("/jobs/api/:1")
    public void json(WebContext ctx, String jobType) {

    }
}
