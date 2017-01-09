/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import com.google.common.collect.Lists;
import sirius.biz.model.TraceData;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.web.BizController;
import sirius.biz.web.PageHelper;
import sirius.db.mixing.SmartQuery;
import sirius.kernel.commons.Context;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.HandledException;
import sirius.web.controller.Controller;
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Message;
import sirius.web.controller.Page;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;

import java.util.Comparator;
import java.util.List;

@Register(classes = Controller.class)
public class JobsController extends BizController {

    @Part
    private Jobs jobs;

    @Routed("/jobs")
    @DefaultRoute
    public void jobs(WebContext ctx) {
        Page<JobDescription> page = new Page<>();
        page.bindToRequest(ctx);
        page.withItems(jobs.findJobs(page.getQuery()));
        ctx.respondWith().template("view/jobs/jobs.html", page);
    }

    @Routed("/job/:1/:2/run")
    public void runJob(WebContext ctx, String factory, String jobName) {
        JobDescription job = jobs.resolve(factory, jobName);
        List<JobParameterDescription> params = fetchParameterDescriptions(job);
        if (ctx.isPOST()) {
            try {
                Context context = Context.create();
                boolean hasWarnings = loadParameters(ctx, params, context);
                if (!hasWarnings) {
                    if (job.verifyParameters(context)) {

                        //TODO execute
                        //TODO job is executing page....
                        String taskId = jobs.execute(job, context);
                        ctx.respondWith().redirectTemporarily("/system/task/" + taskId);
                        return;
                    } else {
                        //TODO
                        UserContext.message(Message.error("TODO"));
                    }
                }
            } catch (Exception e) {
                UserContext.handle(e);
            }
        }
        ctx.respondWith().template("view/jobs/job.html", job);
    }

    @Routed("/job/:1/:2/logs")
    public void jobLogs(WebContext ctx, String factory, String jobName) {
        SmartQuery<JobProtocol> baseQuery = oma.select(JobProtocol.class)
                                               .orderDesc(JobProtocol.TRACE.inner(TraceData.CREATED_AT))
                                               .eq(JobProtocol.FACTORY, factory)
                                               .eq(JobProtocol.JOB, jobName);
        if (!hasPermission(TenantUserManager.PERMISSION_SYSTEM_TENANT)) {
            baseQuery.eq(JobProtocol.TENANT, getUser().getTenantId());
        }
        PageHelper<JobProtocol> ph = PageHelper.withQuery(baseQuery);
        ph.withContext(ctx);
        ph.withSearchFields(JobProtocol.JOB_TITLE, JobProtocol.USER_NAME);
        ctx.respondWith().template("view/jobs/protocols.html", ph);
    }

    public boolean loadParameters(WebContext ctx, List<JobParameterDescription> params, Context context) {
        boolean hasWarnings = false;
        for (JobParameterDescription param : params) {
            try {
                Object value = param.getParameterHandler().convert(ctx.get(param.getName()));
                if (value == null && param.isRequired() && !hasWarnings) {
                    // TODO
                    UserContext.message(Message.error("BLABLA"));
                    hasWarnings = true;
                }
                context.put(param.getName(), value);
            } catch (HandledException t) {
                UserContext.get().addFieldError(param.getName(), t.getMessage());
                hasWarnings = true;
            }
        }
        return hasWarnings;
    }

    public List<JobParameterDescription> fetchParameterDescriptions(JobDescription job) {
        List<JobParameterDescription> params = Lists.newArrayList();
        job.collectParameters(params::add);
        params.sort(Comparator.comparingInt(JobParameterDescription::getPriority));
        return params;
    }
}
