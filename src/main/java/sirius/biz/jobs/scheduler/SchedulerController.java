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
import sirius.biz.web.BasePageHelper;
import sirius.biz.web.BizController;
import sirius.biz.web.TenantAware;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.query.QueryField;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;
import sirius.web.controller.AutocompleteHelper;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

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

    /**
     * Returns the entity class being used by this controller.
     *
     * @return the entity class being used by this controller.
     */
    protected abstract Class<J> getEntryType();

    /**
     * Lists all scheduler entries for the current tenant.
     *
     * @param ctx the current request
     */
    @Permission(PERMISSION_MANAGE_SCHEDULER)
    @Routed("/jobs/scheduler")
    public void schedulerEntries(WebContext ctx) {
        BasePageHelper<J, ?, ?, ?> pageHelper = getEntriesAsPage();
        pageHelper.withContext(ctx);
        pageHelper.addBooleanFacet(SchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.ENABLED).getName(),
                                   NLS.get("SchedulerData.enabled"));
        pageHelper.withSearchFields(QueryField.contains(SchedulerEntry.JOB_CONFIG_DATA.inner(JobConfigData.JOB_NAME)),
                                    QueryField.contains(SchedulerEntry.JOB_CONFIG_DATA.inner(JobConfigData.LABEL)));

        ctx.respondWith().template("/templates/biz/jobs/scheduler/entries.html.pasta", pageHelper.asPage());
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
     * @param ctx     the current request
     * @param entryId the id of the entry to display or <tt>new</tt> to create a new one
     */
    @Permission(PERMISSION_MANAGE_SCHEDULER)
    @Routed("/jobs/scheduler/entry/:1")
    public void schedulerEntry(WebContext ctx, String entryId) {
        J entry = findForTenant(getEntryType(), entryId);

        if (handleJobSelection(entry, ctx)) {
            return;
        }

        if (handleNewEntryWithoutJob(entry, ctx)) {
            return;
        }

        boolean requestHandled = prepareSave(ctx).withAfterSaveURI("/jobs/scheduler").withPreSaveHandler(isNew -> {
            loadUser(ctx, entry);
            entry.getJobConfigData().loadFromContext(ctx);
        }).saveEntity(entry);

        if (!requestHandled) {
            validate(entry);
            ctx.respondWith().template("/templates/biz/jobs/scheduler/entry.html.pasta", entry);
        }
    }

    protected void loadUser(WebContext ctx, J entry) {
        UserInfo user = UserContext.get()
                                   .getUserManager()
                                   .findUserByUserId(ctx.get(SchedulerEntry.SCHEDULER_DATA.inner(SchedulerData.USER_ID)
                                                                                          .toString()).asString());

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

    private boolean handleJobSelection(J entry, WebContext ctx) {
        if (entry.isNew() && ctx.isSafePOST()) {
            if (ctx.hasParameter("selectedJob")) {
                entry.getJobConfigData().setJob(ctx.get("selectedJob").asString());
                ctx.respondWith().template("/templates/biz/jobs/scheduler/entry.html.pasta", entry);
                return true;
            } else if (ctx.hasParameter("job")) {
                entry.getJobConfigData().setJob(ctx.get("job").asString());
            }
        }

        return false;
    }

    private boolean handleNewEntryWithoutJob(J entry, WebContext ctx) {
        if (Strings.isEmpty(entry.getJobConfigData().getJob())) {
            ctx.respondWith().template("/templates/biz/jobs/scheduler/create-entry.html.pasta", entry);
            return true;
        }

        return false;
    }

    /**
     * Deletes the given scheduler entry.
     *
     * @param ctx     the curren request
     * @param entryId the id of the entry to delete
     */
    @Permission(PERMISSION_MANAGE_SCHEDULER)
    @Routed("/jobs/scheduler/entry/:1/delete")
    public void deleteEntry(WebContext ctx, String entryId) {
        deleteEntity(ctx, getEntryType(), entryId);
        schedulerEntries(ctx);
    }

    /**
     * Autocompletion for schedulable background jobs.
     *
     * @param ctx the current request
     */
    @Permission(PERMISSION_MANAGE_SCHEDULER)
    @Routed("/jobs/scheduler/autocomplete")
    public void backgroundJobsAutocomplete(final WebContext ctx) {
        AutocompleteHelper.handle(ctx, (query, result) -> {
            jobs.getAvailableJobs(query)
                .filter(JobFactory::canStartInBackground)
                .limit(AutocompleteHelper.DEFAULT_LIMIT)
                .forEach(factory -> result.accept(AutocompleteHelper.suggest(factory.getName())
                                                                    .withFieldLabel(factory.getLabel())
                                                                    .withCompletionDescription(factory.getDescription())));
        });
    }
}
