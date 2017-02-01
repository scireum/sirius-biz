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
import sirius.biz.tenants.UserAccount;
import sirius.db.mixing.OMA;
import sirius.db.mixing.SmartQuery;
import sirius.kernel.async.CallContext;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Parts;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;
import sirius.web.tasks.ManagedTaskContext;
import sirius.web.tasks.ManagedTasks;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Created by aha on 22.07.16.
 */
@Register(classes = Jobs.class)
public class Jobs {

    public static final Log LOG = Log.get("jobs");
    public static final String PERMISSION_EXECUTE_JOBS = "permission-execute-jobs";
    public static final String FEATURE_PROVIDE_JOBS = "feature-provide-jobs";

    @Parts(JobsFactory.class)
    private Collection<JobsFactory> factories;

    @Part
    private GlobalContext ctx;

    @Part
    private ManagedTasks tasks;

    @Part
    private OMA oma;

    @ConfigValue("jobs.max-logs")
    private int maxLogs;

    @ConfigValue("jobs.keep-logs-days")
    private int keepLogInDays;

    public List<JobDescription> findJobs(@Nullable String query) {
        List<JobDescription> result = Lists.newArrayList();
        for (JobsFactory factory : factories) {
            factory.collectJobs(query, descriptor -> {
                if (hasRequiredPermissions(descriptor)) {
                    result.add(descriptor);
                }
            });
        }

        result.sort(Comparator.comparingInt(JobDescription::getPriority).thenComparing(JobDescription::getTitle));
        return result;
    }

    private boolean hasRequiredPermissions(JobDescription descriptor) {
        UserInfo userInfo = UserContext.getCurrentUser();
        if (!userInfo.hasPermission(PERMISSION_EXECUTE_JOBS)) {
            return false;
        }
        for (String permission : descriptor.getPermissions()) {
            if (!userInfo.hasPermission(permission)) {
                return false;
            }
        }

        return true;
    }

    public JobDescription resolve(String factory, String name) {
        JobsFactory jobsFactory = ctx.findPart(factory, JobsFactory.class);
        JobDescription jobDescription = jobsFactory.resolve(name);
        if (hasRequiredPermissions(jobDescription)) {
            return jobDescription;
        } else {
            return null;
        }
    }

    public String execute(JobDescription job, sirius.kernel.commons.Context context) {
        return tasks.createManagedTaskSetup(job.getTaskTitle(context))
                    .withCategory(job.getPreferredExecutor())
                    .execute(mtc -> executeInOwnThread(job, mtc, context))
                    .getId();
    }

    private void executeInOwnThread(JobDescription job, ManagedTaskContext mtc, sirius.kernel.commons.Context context) {
        JobProtocol protocol = prepareProtocol(job, context);
        try {
            job.execute(context, mtc);
        } catch (Throwable e) {
            mtc.log(Exceptions.handle(LOG, e).getMessage());
            mtc.markErroneous();
        } finally {
            completeProtocol(mtc, protocol);
            deleteOldLogs(job.getFactory(), job.getName());
        }
    }

    private void completeProtocol(ManagedTaskContext mtc, JobProtocol protocol) {
        StringBuilder sb = new StringBuilder();
        mtc.getLastLogs().forEach(e -> sb.append(e).append("\n"));
        protocol.setJobLog(sb.toString());
        protocol.setDurationInSeconds(TimeUnit.MILLISECONDS.toSeconds(CallContext.getCurrent()
                                                                                 .getWatch()
                                                                                 .elapsedMillis()));
        oma.update(protocol);
    }

    private JobProtocol prepareProtocol(JobDescription job, sirius.kernel.commons.Context context) {
        JobProtocol protocol = new JobProtocol();
        protocol.setJob(job.getName());
        protocol.setFactory(job.getFactory());
        protocol.setUser(UserContext.getCurrentUser().getUserId());
        String userName = Optional.ofNullable(UserContext.getCurrentUser().getUserObject(UserAccount.class))
                                  .map(UserAccount::toString)
                                  .orElse(UserContext.getCurrentUser().getUserName());
        protocol.setUserName(userName);
        protocol.setTenant(UserContext.getCurrentUser().getTenantId());
        protocol.setSuccessful(true);
        protocol.setJobTitle(job.getTaskTitle(context));
        oma.update(protocol);
        return protocol;
    }

    private void deleteOldLogs(String factory, String name) {
        try {
            SmartQuery<JobProtocol> qry = oma.select(JobProtocol.class)
                                             .eq(JobProtocol.FACTORY, factory)
                                             .eq(JobProtocol.JOB, name)
                                             .orderAsc(JobProtocol.TRACE.inner(TraceData.CREATED_AT));
            while (qry.count() > maxLogs) {
                JobProtocol protocolToDelete = qry.queryFirst();
                if (protocolToDelete == null) {
                    return;
                }
                LocalDateTime minAge = LocalDate.now().minusDays(keepLogInDays).atStartOfDay();
                if (protocolToDelete.getTrace().getChangedAt().isAfter(minAge)) {
                    return;
                }
                oma.forceDelete(protocolToDelete);
            }
        } catch (Exception e) {
            Exceptions.handle(LOG, e);
        }
    }
}
