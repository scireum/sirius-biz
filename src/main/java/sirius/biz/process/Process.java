/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.biz.elastic.SearchContent;
import sirius.biz.elastic.SearchableEntity;
import sirius.biz.jobs.JobFactory;
import sirius.biz.jobs.Jobs;
import sirius.biz.jobs.batch.BatchProcessJobFactory;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.process.output.ProcessOutput;
import sirius.biz.storage.layer2.BlobContainer;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.es.annotations.ESOption;
import sirius.db.es.annotations.IndexMode;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.BeforeDelete;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.ComplexDelete;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Versioned;
import sirius.db.mixing.types.NestedList;
import sirius.db.mixing.types.StringIntMap;
import sirius.db.mixing.types.StringList;
import sirius.db.mixing.types.StringMap;
import sirius.kernel.commons.Amount;
import sirius.kernel.commons.NumberFormat;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Average;
import sirius.kernel.nls.NLS;
import sirius.web.security.UserContext;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents recordings of a background process.
 * <p>
 * There are two types of processes. Simple processes are started, executed (on one or many nodes) and then completed.
 * Standby processes are created when first used (e.g. by an API call) and then remain in their standby state. These
 * are used to record logs and outputs of API calls or other re-occurring background events which need to communicate
 * with or report to their user / tenant.
 */
@Framework(Processes.FRAMEWORK_PROCESSES)
@ComplexDelete(false)
@Versioned
public class Process extends SearchableEntity {

    @Part
    private static Jobs jobs;

    /**
     * Contains the duration after which auto-refreshing of the details page stops once a process has completed.
     */
    private static final Duration MIN_COMPLETION_TIME_TO_DISABLE_AUTO_REFRESH = Duration.ofSeconds(20);

    /**
     * Contains the name of the {@link sirius.biz.storage.layer2.BlobStorageSpace} which stores the blobs / files
     * which are attached to processes.
     */
    private static final String SPACE_NAME_PROCESSES = "processes";

    /**
     * Contains the title or short description of the process.
     */
    public static final Mapping TITLE = Mapping.named("title");
    @SearchContent
    @IndexMode(indexed = ESOption.FALSE, docValues = ESOption.FALSE)
    private String title;

    /**
     * Contains the icon which best represents the process.
     */
    public static final Mapping ICON = Mapping.named("icon");
    @NullAllowed
    private String icon;

    /**
     * Contains the last "state" as message which is shown in the UI.
     */
    public static final Mapping STATE_MESSAGE = Mapping.named("stateMessage");
    @SearchContent
    @NullAllowed
    @IndexMode(indexed = ESOption.FALSE, docValues = ESOption.FALSE)
    private String stateMessage;

    /**
     * Contains the permission which is required to view this process.
     */
    public static final Mapping REQUIRED_PERMISSION = Mapping.named("requiredPermission");
    @NullAllowed
    private String requiredPermission;

    /**
     * Contains the type of the process.
     * <p>
     * This can be filled by any process but is required for standby processes to identify which process to use.
     */
    public static final Mapping PROCESS_TYPE = Mapping.named("processType");
    @NullAllowed
    private String processType;

    /**
     * Contains the user associated with the process.
     */
    public static final Mapping USER_ID = Mapping.named("userId");
    @NullAllowed
    private String userId;

    /**
     * Contains the name of the user associated with the process.
     */
    public static final Mapping USER_NAME = Mapping.named("userName");
    @SearchContent
    @NullAllowed
    private String userName;

    /**
     * Contains the tenant associated with the process.
     */
    public static final Mapping TENANT_ID = Mapping.named("tenantId");
    private String tenantId;

    /**
     * Contains the name of the tenant associated with the process.
     */
    public static final Mapping TENANT_NAME = Mapping.named("tenantName");
    @SearchContent
    private String tenantName;

    /**
     * Contains parameters which were passed to the process.
     */
    public static final Mapping CONTEXT = Mapping.named("context");
    private final StringMap context = new StringMap();

    /**
     * Contains references used to find this process again.
     */
    public static final Mapping REFERENCES = Mapping.named("references");
    private final StringList references = new StringList();

    /**
     * Contains links generated by the process.
     */
    public static final Mapping LINKS = Mapping.named("links");
    private final NestedList<ProcessLink> links = new NestedList<>(ProcessLink.class);

    /**
     * Contains a list of additional outputs generated by the process.
     */
    public static final Mapping OUTPUTS = Mapping.named("outputs");
    private final NestedList<ProcessOutput> outputs = new NestedList<>(ProcessOutput.class);

    /**
     * Contains files generated by the process.
     */
    public static final Mapping FILES = Mapping.named("files");
    private final BlobContainer files = new BlobContainer(this, SPACE_NAME_PROCESSES);

    /**
     * Contains performance counters provided by the process.
     * <p>
     * Each counter contains the number of events recorded.
     */
    public static final Mapping COUNTERS = Mapping.named("performanceCounters");
    private final StringIntMap performanceCounters = new StringIntMap();

    /**
     * Contains performance counters provided by the process that are meant for administrators.
     * <p>
     * Each counter contains the number of events recorded.
     */
    public static final Mapping ADMIN_COUNTERS = Mapping.named("adminPerformanceCounters");
    private final StringIntMap adminPerformanceCounters = new StringIntMap();

    /**
     * Contains performance timers provided by the process.
     * <p>
     * Each timing contains the average execution time in milliseconds.
     */
    public static final Mapping TIMINGS = Mapping.named("timings");
    private final StringIntMap timings = new StringIntMap();

    /**
     * Contains performance timers provided by the process that are meant for administrators.
     * <p>
     * Each timing contains the average execution time in milliseconds.
     */
    public static final Mapping ADMIN_TIMINGS = Mapping.named("adminTimings");
    private final StringIntMap adminTimings = new StringIntMap();

    /**
     * Contains the timestamp when the process was created / initialized.
     * <p>
     * Note, for standby processes, this contains the timestamp of the last invocation.
     */
    public static final Mapping CREATED = Mapping.named("created");
    @NullAllowed
    private LocalDateTime created;

    /**
     * Contains the timestamp when the process was started.
     * <p>
     * Note, for standby processes, this contains the timestamp of the last invocation.
     */
    public static final Mapping STARTED = Mapping.named("started");
    @NullAllowed
    private LocalDateTime started;

    /**
     * Contains the timestamp when the process was canceled.
     */
    public static final Mapping CANCELED = Mapping.named("canceled");
    @NullAllowed
    private LocalDateTime canceled;

    /**
     * Contains the timestamp when the process was completed.
     */
    public static final Mapping COMPLETED = Mapping.named("completed");
    @NullAllowed
    private LocalDateTime completed;

    /**
     * Contains the waiting time in seconds.
     * <p>
     * This is the time between the creation of the process and the actual start of the process.
     */
    public static final Mapping WAITING_TIME = Mapping.named("waitingTime");
    @NullAllowed
    private int waitingTime;

    /**
     * Contains the estimated computation time performed in this process in seconds.
     * <p>
     * Note that this is not an exact value, as we might skip very short computations (esp. for standby processes)
     * to reduce the overhead for Elasticsearch.
     */
    public static final Mapping COMPUTATION_TIME = Mapping.named("computationTime");
    @NullAllowed
    private int computationTime;

    /**
     * Contains the date when the process will be deleted.
     */
    public static final Mapping EXPIRES = Mapping.named("expires");
    @NullAllowed
    private LocalDate expires;

    /**
     * Contains period for which this process will be kept.
     * <p>
     * For {@link ProcessState#STANDBY standby} processes this denotes the duration for which logs will be kept.
     */
    public static final Mapping PERSISTENCE_PERIOD = Mapping.named("persistencePeriod");
    private PersistencePeriod persistencePeriod = PersistencePeriod.THREE_MONTHS;

    /**
     * Determines if this process failed or encountered errors during its execution.
     */
    public static final Mapping ERRORNEOUS = Mapping.named("errorneous");
    private boolean errorneous;

    /**
     * Determines if this process encountered warnings but no errors during its execution.
     */
    public static final Mapping WARNINGS = Mapping.named("warnings");
    private boolean warnings;

    /**
     * Determines if this process has debugging enabled.
     * <p>
     * This permits to add tracing logs and other instrumentation which can be enabled on demand.
     *
     * @see Processes#changeDebugging(String, boolean)
     * @see ProcessContext#debug(ProcessLog)
     * @see ProcessContext#addDebugTiming(String, long)
     */
    public static final Mapping DEBUGGING = Mapping.named("debugging");
    private boolean debugging;

    /**
     * Contains the state of this process.
     */
    public static final Mapping STATE = Mapping.named("state");
    private ProcessState state;

    @Part
    @Nullable
    private static Processes processes;

    @BeforeSave
    protected void beforeSave() {
        if (persistencePeriod == null) {
            if (state == ProcessState.STANDBY) {
                persistencePeriod = PersistencePeriod.THREE_MONTHS;
            } else {
                persistencePeriod = PersistencePeriod.SIX_YEARS;
            }
        }
    }

    @BeforeDelete
    @ComplexDelete
    protected void truncateLogs() {
        // Deletes all process logs using delete-by-query
        elastic.select(ProcessLog.class).eq(ProcessLog.PROCESS, getId()).truncate();
    }

    /**
     * Formats a timestamp by omitting the date if it is "today".
     *
     * @param timestamp the timestamp to format
     * @return a string representation of the given timestamp
     */
    public static String formatTimestamp(LocalDateTime timestamp) {
        if (timestamp == null) {
            return "";
        }

        if (timestamp.toLocalDate().equals(LocalDate.now())) {
            return NLS.toUserString(timestamp.toLocalTime());
        }

        return NLS.toUserString(timestamp);
    }

    /**
     * Returns the started timestamp formatted as string.
     *
     * @return the started timestamp as string
     */
    public String getStartedAsString() {
        return formatTimestamp(getStarted());
    }

    /**
     * Returns the completed timestamp formatted as string.
     *
     * @return the completed timestamp as string
     */
    public String getCompletedAsString() {
        return formatTimestamp(getCompleted());
    }

    /**
     * Returns the expiry date formatted as string.
     *
     * @return the expiry date as string
     */
    public String getExpiresAsString() {
        return NLS.toUserString(getExpires());
    }

    /**
     * Returns the runtime (duration of the process) as string.
     *
     * @return the duration of the process formatted as string
     */
    @Nullable
    public String getRuntimeAsString() {
        if (getComputationTime() > 0) {
            return formatDuration(getComputationTime());
        }
        if (getStarted() == null || getCompleted() == null || state == ProcessState.STANDBY) {
            return null;
        }

        // This is only required for legacy processes which have no "computationTime" (as it was newly introduced)...
        int estimatedRuntime = (int) Duration.between(getStarted(), getCompleted()).getSeconds();
        return formatDuration(estimatedRuntime);
    }

    private String formatDuration(int seconds) {
        return Strings.apply("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    /**
     * Determines if the details view of the process should keep on auto-refreshing.
     *
     * @return <tt>true</tt> if the page should keep auto refreshing, <tt>false</tt> if the process completed and
     * no more changes are expected
     */
    public boolean shouldAutorefresh() {
        if (getCompleted() == null || getState() != ProcessState.TERMINATED) {
            return true;
        }

        return Duration.between(getCompleted(), LocalDateTime.now())
                       .compareTo(MIN_COMPLETION_TIME_TO_DISABLE_AUTO_REFRESH) < 0;
    }

    /**
     * Determines the dot color to use when rendering the state.
     *
     * @return the color used for the state of this process
     */
    public String getStateColor() {
        return switch (state) {
            case WAITING, RUNNING -> "blue";
            case STANDBY -> "violet-light";
            case TERMINATED -> "green";
            case CANCELED -> "yellow";
        };
    }

    public String getMessage() {
        if (Strings.isFilled(stateMessage)) {
            return stateMessage;
        }

        String counters = getCounterList().stream()
                                          .limit(5)
                                          .map(counter -> getCounterLabel(counter) + ": " + NLS.toUserString(
                                                  getCounterValue(counter)))
                                          .collect(Collectors.joining(", "));
        if (Strings.isEmpty(counters)) {
            return "";
        }

        return NLS.get("Process.counters") + ": " + counters;
    }

    /**
     * Returns the names of counters recorded for this process that the user has access to.
     *
     * @return the names of counters recorded for this process that the user has access to
     */
    public List<String> getCounterList() {
        Stream<String> counterStream = performanceCounters.data().keySet().stream();

        if (UserContext.getCurrentUser().hasPermission(Tenant.PERMISSION_SYSTEM_TENANT)) {
            counterStream = Stream.concat(counterStream, adminPerformanceCounters.data().keySet().stream());
        }

        return counterStream.sorted().toList();
    }

    /**
     * Returns the counter label.
     *
     * @param name the counter to fetch the label for
     * @return either the translated label (if a matching property exists) or the counter name itself
     */
    public String getCounterLabel(String name) {
        return NLS.getIfExists(name, null).orElse(name);
    }

    /**
     * Returns the counter value for the given counter name.
     *
     * @param name the counter to read
     * @return the count value of the given counter
     */
    public String getCounterValue(String name) {
        Integer value = performanceCounters.get(name).or(() -> adminPerformanceCounters.get(name)).orElse(0);
        return Amount.of(value).toString(NumberFormat.NO_DECIMAL_PLACES).asString();
    }

    /**
     * Returns the average duration in milliseconds for the given counter
     *
     * @param name the counter to read
     * @return the average duration in millis (readily formatted as string) or an empty string is the average
     * is zero or less
     */
    public String getCounterTiming(String name) {
        Integer counter = timings.get(name).or(() -> adminTimings.get(name)).orElse(0);

        return counter > 0 ? Strings.apply("%s ms", counter) : "";
    }

    /**
     * Returns the icon used to visualize this process.
     *
     * @return the icon to be used for this process¡
     */
    public String getIcon() {
        if (Strings.isEmpty(icon)) {
            if (getState() == ProcessState.STANDBY) {
                return "fa-retweet";
            } else {
                return "fa-cogs";
            }
        }

        return icon;
    }

    /**
     * Adds or updates the timing with the given name.
     *
     * @param name    the name of the timing
     * @param average contains the count and average time of the events
     */
    public void addTiming(String name, Average average) {
        performanceCounters.put(name, (int) average.getCount());
        timings.put(name, (int) average.getAvg());
    }

    /**
     * Adds or updates the timing with the given name which is only visible to administrators.
     *
     * @param name    the name of the timing
     * @param average contains the count and average time of the events
     */
    public void addAdminTiming(String name, Average average) {
        adminPerformanceCounters.put(name, (int) average.getCount());
        adminTimings.put(name, (int) average.getAvg());
    }

    /**
     * Checks if there are more visible outputs than the standard one.
     *
     * @return true, if there are more than the standard output visible to the current user
     */
    public boolean hasVisibleOutputs() {
        if (getOutputs().isEmpty()) {
            return false;
        }
        return getOutputs().data().stream().anyMatch(output -> !output.isSystemOutput()) || UserContext.getCurrentUser()
                                                                                                       .hasPermission(
                                                                                                               ProcessController.PERMISSION_MANAGE_ALL_PROCESSES);
    }

    /**
     * Determines if the persistence period can be changed for this process.
     * <p>
     * This is defined by the underlying {@linkplain BatchProcessJobFactory process type}. If we cannot resolve
     * the process type, or it is not a batch process, we assume that the persistence period can be changed.
     *
     * @return {@code true} if the persistence period can be changed, {@code false} otherwise
     */
    public boolean canChangePersistencePeriod() {
        if (UserContext.getCurrentUser().hasPermission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)) {
            // Administrators can always change the persistence period
            return true;
        }

        try {
            JobFactory factory = jobs.findFactory(processType, JobFactory.class);
            if (factory instanceof BatchProcessJobFactory processJobFactory) {
                return processJobFactory.canChangePersistencePeriod();
            } else {
                return true;
            }
        } catch (Exception _) {
            return true;
        }
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getProcessType() {
        return processType;
    }

    public void setProcessType(String processType) {
        this.processType = processType;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public StringMap getContext() {
        return context;
    }

    public NestedList<ProcessLink> getLinks() {
        return links;
    }

    public StringIntMap getPerformanceCounters() {
        return performanceCounters;
    }

    public StringIntMap getAdminPerformanceCounters() {
        return adminPerformanceCounters;
    }

    public StringIntMap getTimings() {
        return timings;
    }

    public StringIntMap getAdminTimings() {
        return adminTimings;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public Process setCreated(LocalDateTime created) {
        this.created = created;
        return this;
    }

    public LocalDateTime getStarted() {
        return started;
    }

    public void setStarted(LocalDateTime started) {
        this.started = started;
    }

    public LocalDateTime getCompleted() {
        return completed;
    }

    public void setCompleted(LocalDateTime completed) {
        this.completed = completed;
    }

    public ProcessState getState() {
        return state;
    }

    public void setState(ProcessState state) {
        this.state = state;
    }

    public String getStateMessage() {
        return stateMessage;
    }

    public void setStateMessage(String stateMessage) {
        this.stateMessage = stateMessage;
    }

    public boolean isErrorneous() {
        return errorneous;
    }

    public void setErrorneous(boolean errorneous) {
        this.errorneous = errorneous;
    }

    public boolean isDebugging() {
        return debugging;
    }

    public void setDebugging(boolean debugging) {
        this.debugging = debugging;
    }

    public LocalDateTime getCanceled() {
        return canceled;
    }

    public void setCanceled(LocalDateTime canceled) {
        this.canceled = canceled;
    }

    public String getRequiredPermission() {
        return requiredPermission;
    }

    public void setRequiredPermission(String requiredPermission) {
        this.requiredPermission = requiredPermission;
    }

    public NestedList<ProcessOutput> getOutputs() {
        return outputs;
    }

    public BlobContainer getFiles() {
        return files;
    }

    public StringList getReferences() {
        return references;
    }

    public LocalDate getExpires() {
        return expires;
    }

    public void setExpires(LocalDate expires) {
        this.expires = expires;
    }

    public PersistencePeriod getPersistencePeriod() {
        return persistencePeriod;
    }

    public void setPersistencePeriod(PersistencePeriod persistencePeriod) {
        this.persistencePeriod = persistencePeriod;
    }

    public boolean isWarnings() {
        return warnings;
    }

    public void setWarnings(boolean warnings) {
        this.warnings = warnings;
    }

    public int getWaitingTime() {
        return waitingTime;
    }

    public Process setWaitingTime(int waitingTime) {
        this.waitingTime = waitingTime;
        return this;
    }

    public int getComputationTime() {
        return computationTime;
    }

    public void setComputationTime(int computationTime) {
        this.computationTime = computationTime;
    }
}
