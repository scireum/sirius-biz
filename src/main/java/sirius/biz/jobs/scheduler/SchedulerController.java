/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.scheduler;

import sirius.biz.jobs.JobConfigData;
import sirius.biz.jobs.JobFactory;
import sirius.biz.jobs.Jobs;
import sirius.biz.process.Processes;
import sirius.biz.web.BasePageHelper;
import sirius.biz.web.BizController;
import sirius.biz.web.TenantAware;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.query.QueryField;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Urls;
import sirius.kernel.di.PartCollection;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Parts;
import sirius.kernel.nls.NLS;
import sirius.web.controller.AutocompleteHelper;
import sirius.web.controller.Message;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import java.time.LocalDateTime;

/**
 * Provides a base class to create the management UI for the job scheduler.
 * <p>
 * An implementation per database technology will be used to provide the actual functionality.
 *
 * @param <J> the generic type of entities being managed by this controller.
 */
public abstract class SchedulerController<J extends BaseEntity<?> & SchedulerEntry & TenantAware>
        extends BizController {

    public static final String PERMISSION_MANAGE_SCHEDULER = "permission-manage-scheduler";

    @Part
    private Jobs jobs;

    @Part
    private Processes processes;

    @Parts(SchedulerEntryProvider.class)
    private PartCollection<SchedulerEntryProvider<J>> providers;

    @Part
    private ScheduledEntryExecution entryExecution;

    /**
     * Returns the entity class being used by this controller.
     *
     * @return the entity class being used by this controller.
     */
    protected abstract Class<J> getEntryType();

    /**
     * Lists all scheduler entries for the current tenant.
     *
     * @param webContext the current request
     */
    @Permission(PERMISSION_MANAGE_SCHEDULER)
    @Routed("/jobs/scheduler")
    public void schedulerEntries(WebContext webContext) {
        BasePageHelper<J, ?, ?, ?> pageHelper = getEntriesAsPage();
        pageHelper.withContext(webContext);
        pageHelper.addBooleanFacet(SchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.ENABLED).getName(),
                                   NLS.get("SchedulerData.enabled"));
        pageHelper.withSearchFields(QueryField.contains(SchedulerEntry.JOB_CONFIG_DATA.inner(JobConfigData.JOB_NAME)),
                                    QueryField.contains(SchedulerEntry.JOB_CONFIG_DATA.inner(JobConfigData.LABEL)));

        webContext.respondWith().template("/templates/biz/jobs/scheduler/entries.html.pasta", pageHelper.asPage());
    }

    /**
     * Retrieves the scheduler entries for the current tenant as {@link BasePageHelper}.
     *
     * @return the page helper representing all entries of this tenant
     */
    protected abstract BasePageHelper<J, ?, ?, ?> getEntriesAsPage();

    /**
     * Renders a details page for the given scheduler entry.
     *
     * @param webContext the current request
     * @param entryId    the id of the entry to display or <tt>new</tt> to create a new one
     */
    @Permission(PERMISSION_MANAGE_SCHEDULER)
    @Routed("/jobs/scheduler/entry/:1")
    public void schedulerEntry(WebContext webContext, String entryId) {
        J entry = findForTenant(getEntryType(), entryId);

        if (handleJobSelection(entry, webContext)) {
            return;
        }

        if (handleNewEntryWithoutJob(entry, webContext)) {
            return;
        }

        boolean requestHandled =
                prepareSave(webContext).withAfterSaveURI("/jobs/scheduler").withPreSaveHandler(isNew -> {
                    loadUser(webContext, entry);
                    entry.getJobConfigData().loadFromContext(webContext);
                }).saveEntity(entry);

        if (!requestHandled) {
            validate(entry);
            webContext.respondWith().template("/templates/biz/jobs/scheduler/entry.html.pasta", entry);
        }
    }

    /**
     * Executes the given scheduler entry immediately and redirects to the process detail view.
     *
     * @param webContext the current request
     * @param entryId    the id of the entry to display or <tt>new</tt> to create a new one
     */
    @Permission(Jobs.PERMISSION_EXECUTE_JOBS)
    @Routed("/jobs/scheduler/:1/execute")
    public void executeSchedulerEntry(WebContext webContext, String entryId) {
        J entry = findForTenant(getEntryType(), entryId);

        if (entry.isNew()) {
            schedulerEntries(webContext);
            return;
        }

        executeInBelongingProvider(entry);
        String entryProcessesUrl = Strings.apply("/ps?reference=%s&reference-label=%s",
                                                 entry.getUniqueName(),
                                                 Urls.encode(entry.toString()));
        webContext.respondWith().redirectToGet(entryProcessesUrl);
    }

    private void executeInBelongingProvider(J entry) {
        for (SchedulerEntryProvider<J> provider : providers) {
            J entryFromProvider = provider.fetchFullInformation(entry);
            if (entryBelongsToProvider(entry, entryFromProvider)) {
                entryExecution.executeJob(provider, entryFromProvider, LocalDateTime.now());
                return;
            }
        }
    }

    private static <J extends BaseEntity<?> & SchedulerEntry & TenantAware> boolean entryBelongsToProvider(J entry,
                                                                                                           J providersEntry) {
        // The belonging provided must have refreshed data, so the object identity is different.
        return System.identityHashCode(providersEntry) != System.identityHashCode(entry);
    }

    protected void loadUser(WebContext webContext, J entry) {
        UserInfo user = UserContext.get()
                                   .getUserManager()
                                   .findUserByUserId(webContext.get(SchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.USER_ID)
                                                                                                 .toString())
                                                               .asString());

        // Ensure that an active and accessible user was selected...
        if (user == null || !Strings.areEqual(UserContext.getCurrentUser().getTenantId(), user.getTenantId())) {
            // If no or an invalid user was given, and we already have one set, we do not change anything...
            if (Strings.isFilled(entry.getSchedulerData().getUserId())) {
                return;
            }

            // If no user is present, we default to the current user...
            user = UserContext.getCurrentUser();
        }

        entry.getSchedulerData().setUserId(user.getUserId());
        entry.getSchedulerData().setUserName(user.getUserName());
    }

    private boolean handleJobSelection(J entry, WebContext webContext) {
        if (entry.isNew()) {
            if (webContext.hasParameter("selectedJob")) {
                String jobName = webContext.get("selectedJob").asString();

                if (jobs.tryFindFactory(jobName).filter(JobFactory::isAccessibleToCurrentUser).isPresent()) {
                    entry.getJobConfigData().setJob(jobName);
                    webContext.respondWith().template("/templates/biz/jobs/scheduler/entry.html.pasta", entry);
                    return true;
                }

                UserContext.get()
                           .addMessage(Message.error()
                                              .withTextMessage(NLS.fmtr("JobsController.unknownJob")
                                                                  .set("jobType", jobName)
                                                                  .format()));
                webContext.respondWith().redirectToGet("/jobs/scheduler");
                return true;
            } else if (webContext.hasParameter("job") && webContext.isSafePOST()) {
                entry.getJobConfigData().setJob(webContext.get("job").asString());
            }
        }

        return false;
    }

    private boolean handleNewEntryWithoutJob(J entry, WebContext webContext) {
        if (Strings.isEmpty(entry.getJobConfigData().getJob())) {
            webContext.respondWith().template("/templates/biz/jobs/scheduler/create-entry.html.pasta", entry);
            return true;
        }

        return false;
    }

    /**
     * Deletes the given scheduler entry.
     *
     * @param webContext the curren request
     * @param entryId    the id of the entry to delete
     */
    @Permission(PERMISSION_MANAGE_SCHEDULER)
    @Routed("/jobs/scheduler/entry/:1/delete")
    public void deleteEntry(WebContext webContext, String entryId) {
        deleteEntity(webContext, getEntryType(), entryId);
        schedulerEntries(webContext);
    }

    /**
     * Autocompletion for schedulable background jobs.
     *
     * @param webContext the current request
     */
    @Permission(PERMISSION_MANAGE_SCHEDULER)
    @Routed("/jobs/scheduler/autocomplete")
    public void backgroundJobsAutocomplete(final WebContext webContext) {
        AutocompleteHelper.handle(webContext, (query, result) -> {
            jobs.getAvailableJobs(query)
                .filter(JobFactory::canStartInBackground)
                .limit(AutocompleteHelper.DEFAULT_LIMIT)
                .forEach(factory -> result.accept(AutocompleteHelper.suggest(factory.getName())
                                                                    .withFieldLabel(factory.getLabel())
                                                                    .withCompletionDescription(factory.getDescription())));
        });
    }
}
