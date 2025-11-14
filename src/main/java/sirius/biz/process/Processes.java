/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.biz.analytics.reports.Cells;
import sirius.biz.elastic.AutoBatchLoop;
import sirius.biz.locks.Locks;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.process.logs.ProcessLogState;
import sirius.biz.process.logs.ProcessLogType;
import sirius.biz.process.output.LogsProcessOutputType;
import sirius.biz.process.output.ProcessOutput;
import sirius.biz.process.output.TableProcessOutputType;
import sirius.biz.protocol.JournalData;
import sirius.biz.storage.layer2.Blob;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.Tenants;
import sirius.db.es.Elastic;
import sirius.db.es.ElasticQuery;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.IntegrityConstraintFailedException;
import sirius.db.mixing.OptimisticLockException;
import sirius.kernel.async.CallContext;
import sirius.kernel.async.DelayLine;
import sirius.kernel.async.TaskContext;
import sirius.kernel.async.TaskContextAdapter;
import sirius.kernel.async.Tasks;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Wait;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Average;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nullable;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Provides the central facility to create and use {@link Process processes}.
 * <p>
 * There are essentially two types of processes. "Normal" ones which run for a certain amount of time and then complete.
 * These can be created via {@link Processes#createProcess(String, String, String, UserInfo, PersistencePeriod, Map)}
 * and then used to execute code within them via {@link Processes#execute(String, Consumer)} or
 * {@link Processes#partiallyExecute(String, Consumer)}. Where the latter executes some code but doesn't complete
 * the process so that other tasks or even other nodes can perform more action within it.
 * <p>
 * The other type of processes are "standby" processes which are created on demand and then "run" forever.
 * These can be used for regular background activity (like a web service interface which needs to report
 * an error every once in a while). From time to time the system will clean up these processes and remove old logs
 * so that the system doesn't overload itself. A standby process can be fetched via
 * {@link Processes#executeInStandbyProcess(String, Supplier, String, Supplier, Consumer)}.
 * <p>
 * Every direct interaction with the {@link Process} should be performed via the provided {@link ProcessContext}.
 */
@Register(classes = Processes.class, framework = Processes.FRAMEWORK_PROCESSES)
public class Processes {

    /**
     * Names the framework which must be enabled to activate the processes feature.
     */
    public static final String FRAMEWORK_PROCESSES = "biz.processes";

    private static final String LOCK_CREATE_STANDBY_PROCESS = "lock-create-standby-process";

    @Part
    private Elastic elastic;

    @Part
    private DelayLine delayLine;

    @Part
    private AutoBatchLoop autoBatch;

    @Part
    private Locks locks;

    @Part
    private Cells cells;

    @Part
    private TableProcessOutputType tableProcessOutputType;

    /**
     * Due to some shortcomings in Elasticsearch (1-second delay until writes are visible), we need a layered cache
     * architecture here.
     * <p>
     * This cache is very short-lived and only used to provide instances which can directly be modified
     * (most probably without even waiting for the "1 second" delay, as long as only one node concurrently modifies
     * a process - which should be quite common).
     */
    private final Cache<String, Process> process1stLevelCache = CacheManager.createLocalCache("processes-first-level");

    /**
     * This is a longer lived "read" cache to pull data from. This is mostly used by the {@link ProcessEnvironment}
     * to fetch "static" data (context, user, ...).
     */
    private final Cache<String, Process> process2ndLevelCache =
            CacheManager.createCoherentCache("processes-second-level");

    /**
     * Due to the write-delay in Elasticsearch, we need to cache processes of type {@link ProcessState#STANDBY} separately.
     * <p>
     * We don't provide a layered cache structure in this case as standby processes are long living and a limited set.
     */
    private final Cache<String, Process> standbyProcessCache = CacheManager.createCoherentCache("standby-processes");

    /**
     * Creates a new process.
     *
     * @param type              the type of the process (which can be used for filtering in the backend)
     * @param title             the title to show in the UI
     * @param icon              the icon to use for this process
     * @param user              the user that belongs to the process
     * @param persistencePeriod the period for which this process is kept
     * @param context           the context passed into the process
     * @return the newly created process
     */
    public String createProcess(@Nullable String type,
                                String title,
                                String icon,
                                UserInfo user,
                                PersistencePeriod persistencePeriod,
                                Map<String, String> context) {
        Process process = new Process();
        process.setTitle(title);
        process.setIcon(icon);
        process.setUserId(user.getUserId());
        process.setUserName(user.getUserName());
        process.setTenantId(user.getTenantId());
        process.setTenantName(user.getTenantName());
        process.setState(ProcessState.WAITING);
        process.setProcessType(type);
        process.setCreated(LocalDateTime.now());
        process.setPersistencePeriod(persistencePeriod);
        process.getContext().modify().putAll(context);

        elastic.update(process);
        process1stLevelCache.put(process.getId(), process);
        process2ndLevelCache.put(process.getId(), process);

        return process.getIdAsString();
    }

    /**
     * Fetches the currently active process.
     *
     * @return the process for which the current thread is executing or an empty optional if no process is active
     */
    public Optional<Supplier<Process>> fetchCurrentProcess() {
        TaskContextAdapter adapter = TaskContext.get().getAdapter();
        if (adapter instanceof ProcessEnvironment processEnvironment) {
            return Optional.of(() -> fetchRequiredProcess(processEnvironment.getProcessId()));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Creates a new process for the currently active user.
     *
     * @param type              the type of the process (which can be used for filtering in the backend)
     * @param title             the title to show in the UI
     * @param icon              the icon to use for this process
     * @param persistencePeriod the period for which this process is kept
     * @param context           the context passed into the process
     * @return the newly created process
     */
    public String createProcessForCurrentUser(@Nullable String type,
                                              String title,
                                              String icon,
                                              PersistencePeriod persistencePeriod,
                                              Map<String, String> context) {
        return createProcess(type, title, icon, UserContext.getCurrentUser(), persistencePeriod, context);
    }

    /**
     * Marks an existing and terminated process as active again.
     * <p>
     * This is used for downstream processing (e.g. writing outputs into a file) after a process has
     * {@link ProcessState#TERMINATED}. Essentially, all this does is verifying the preconditions and setting the
     * state back to {@link ProcessState#RUNNING}.
     * <p>
     * When restarting a currently terminated process, the node which executes the process must purge it from its local
     * cache via {@link #purgeProcessFromFirstLevelCache(String)}. An example for this is the
     * {@linkplain ProcessController#exportOutput(WebContext, String, String, String) export} of process log messages
     * performed in {@link ExportLogsAsFileTaskExecutor}.
     *
     * @param processId the id of the process to restart
     * @param reason    the reason to log
     * @throws sirius.kernel.health.HandledException in case the process isn't currently terminated or doesn't exist at all
     */
    public void restartProcess(String processId, String reason) {
        modify(processId,
               process -> process.getState() == ProcessState.TERMINATED,
               process -> process.setState(ProcessState.RUNNING));
        log(processId, ProcessLog.info().withNLSKey("Processes.restarted").withContext("reason", reason));

        // we need to wait for elastic to propagate process state changes if running on a different machine
        Wait.seconds(2);
    }

    /**
     * Clears the given process from the local cache.
     * <p>
     * There is rarely a need to call this method. Manually purging the process is generally necessary when
     * {@linkplain #restartProcess(String, String) restarting} a currently terminated process. An example for this
     * is the {@linkplain ProcessController#exportOutput(WebContext, String, String, String) export} of process log
     * messages performed in {@link ExportLogsAsFileTaskExecutor}.
     *
     * @param processId the process to purge from the cache
     */
    public void purgeProcessFromFirstLevelCache(String processId) {
        process1stLevelCache.remove(processId);
    }

    /**
     * Executes the given task in the standby process of the given type, for the currently active tenant.
     * <p>
     * If no matching standby process exists, one will be created.
     * <p>
     * Note that a tenant has to be present (a user has to be logged in) or an exception will be thrown.
     *
     * @param type          the type of the standby process to find or create
     * @param titleSupplier a supplier which generates a title if the process has to be created
     * @param task          the task to execute within the process
     * @throws IllegalStateException                 if no user / tenant is present
     * @throws sirius.kernel.health.HandledException in case of an error which occurred while executing the task
     */
    public void executeInStandbyProcessForCurrentTenant(String type,
                                                        Supplier<String> titleSupplier,
                                                        Consumer<ProcessContext> task) {
        UserInfo currentUser = UserContext.getCurrentUser();
        if (!currentUser.isLoggedIn()) {
            throw new IllegalStateException("Cannot execute a standby process without a user / tenant.");
        }

        executeInStandbyProcess(type,
                                titleSupplier,
                                currentUser.getTenantId(),
                                () -> UserContext.getCurrentUser().getTenantName(),
                                task);
    }

    /**
     * Executes the given task in the standby process of the given type, for the given tenant.
     * <p>
     * If no matching standby process exists, one will be created.
     * <p>
     * Note that {@link Tenants#getSystemTenantId()} and {@link Tenants#getSystemTenantName()} can be used for
     * system tasks.
     *
     * @param type               the type of the standby process to find or create
     * @param titleSupplier      a supplier which generates a title if the process has to be created
     * @param tenantId           the id of the tenant used to find the appropriate process
     * @param tenantNameSupplier a supplier which yields the name of the tenant if the process has to be created
     * @param task               the task to execute within the process
     * @throws sirius.kernel.health.HandledException in case of an error which occurred while executing the task
     */
    public void executeInStandbyProcess(String type,
                                        Supplier<String> titleSupplier,
                                        String tenantId,
                                        Supplier<String> tenantNameSupplier,
                                        Consumer<ProcessContext> task) {
        Process process = fetchStandbyProcess(type, tenantId);

        if (process != null) {
            modify(process.getId(), p -> p.getState() == ProcessState.STANDBY, p -> p.setStarted(LocalDateTime.now()));
        } else {
            process = fetchStandbyProcessInLock(type, titleSupplier.get(), tenantId, tenantNameSupplier);
        }

        partiallyExecute(process.getId(), task);
    }

    /**
     * Resolves the given type and tenant id into a standby process.
     * <p>
     * This will first try the cache and then resort to Elasticsearch.
     *
     * @param type     the type of the standby process
     * @param tenantId the tenant for which the process should be fetched
     * @return the process with the given type for the given tenant wrapped as optional or an empty optional if no such
     * process exists
     */
    @Nullable
    private Process fetchStandbyProcess(String type, String tenantId) {
        Process process = standbyProcessCache.get(type + "-" + tenantId);
        if (process != null) {
            return process;
        }

        process = elastic.select(Process.class)
                         .eq(Process.STATE, ProcessState.STANDBY)
                         .eq(Process.TENANT_ID, tenantId)
                         .eq(Process.PROCESS_TYPE, type)
                         .first()
                         .orElse(null);
        if (process != null) {
            standbyProcessCache.put(type + "-" + tenantId, process);
        }

        return process;
    }

    /**
     * Tries to fetch the appropriate standby process while holding a lock and also after waiting an appropriate amount of time.
     *
     * @param type               the type of the standby process to find or create
     * @param title              the title of the process
     * @param tenantId           the id of the tenant used to find the appropriate process
     * @param tenantNameSupplier a supplier which yields the name of the tenant if the process has to be created
     * @return the process which was either resolved after waiting an appropriate amount of time or created
     */
    private Process fetchStandbyProcessInLock(String type,
                                              String title,
                                              String tenantId,
                                              Supplier<String> tenantNameSupplier) {
        String lockName = LOCK_CREATE_STANDBY_PROCESS + "-" + type + "-" + tenantId;
        if (!locks.tryLock(lockName, Duration.ofSeconds(30))) {
            throw Exceptions.handle()
                            .withSystemErrorMessage(
                                    "Cannot acquire a lock (%s} to create or fetch a standby process of type %s for %s (%s)",
                                    lockName,
                                    type,
                                    tenantNameSupplier.get(),
                                    tenantId)
                            .handle();
        }
        try {
            // Maybe another node recently created a matching standby process. Wait a reasonable amount of time so that the change
            // becomes visible in elasticsearch/caches. As standby processes are rarely created it is legitimate to hold the lock
            // while waiting.
            int attempts = 4;
            while (attempts-- > 0) {
                Process process = fetchStandbyProcess(type, tenantId);
                if (process != null) {
                    return process;
                }

                Wait.millis(300);
            }

            return createStandbyProcessInLock(type, title, tenantId, tenantNameSupplier.get());
        } finally {
            locks.unlock(lockName);
        }
    }

    /**
     * Effectively creates a new standby process after we ensured, that it doesn't exist yet (and also isn't created
     * elsewhere in parallel).
     *
     * @param type       the type of the standby process to find or create
     * @param title      the title of the process
     * @param tenantId   the id of the tenant used to find the appropriate process
     * @param tenantName the name of the tenant
     * @return the newly created process
     */
    private Process createStandbyProcessInLock(String type, String title, String tenantId, String tenantName) {
        Process process = new Process();

        process.setTitle(title);
        process.setProcessType(type);
        process.setTenantId(tenantId);
        process.setTenantName(tenantName);
        process.setState(ProcessState.STANDBY);
        process.setCreated(LocalDateTime.now());
        process.setStarted(LocalDateTime.now());
        elastic.update(process);

        process1stLevelCache.put(process.getId(), process);
        process2ndLevelCache.put(process.getId(), process);
        standbyProcessCache.put(type + "-" + tenantId, process);
        return process;
    }

    /**
     * Resolves the given id into a process.
     * <p>
     * This will first try the 1st and 2nc level cache and then resort to Elasticsearch.
     * However, a fetched process is only put into the 2nd level cache once it was resolved
     * as this has a lower lifespan and is appropriate for most reads.
     *
     * @param processId the id of the process to fetch
     * @return the process with the given id wrapped as optional or an empty optional if no such process exists
     */
    protected Optional<Process> fetchProcess(String processId) {
        Process process = process1stLevelCache.get(processId);
        if (process != null) {
            return Optional.of(process);
        }

        process = process2ndLevelCache.get(processId);
        if (process != null) {
            return Optional.of(process);
        }

        process = elastic.find(Process.class, processId).orElse(null);
        if (process != null) {
            // Only cache non-null lookups...
            process2ndLevelCache.put(processId, process);
        }

        return Optional.ofNullable(process);
    }

    private Process fetchRequiredProcess(String processId) {
        return fetchProcess(processId).orElseThrow(() -> new IllegalStateException(Strings.apply(
                "The requested process (%s) isn't available.",
                processId)));
    }

    /**
     * Modifies the process with the given id.
     * <p>
     * This utilizes the 1st level cache to lookup recently modified objects without having to
     * wait for the 1s write delay.
     *
     * @param processId the id of the process to modify
     * @param checker   the predicate to evaluate if the modification should take place
     * @param modifier  the actual modifier which mutates the process
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */
    private boolean modify(String processId, Predicate<Process> checker, Consumer<Process> modifier) {
        Process process = process1stLevelCache.get(processId);
        if (process == null) {
            process = elastic.find(Process.class, processId).orElse(null);
        }

        int retries = 5;
        while (retries-- > 0) {
            if (process == null) {
                return false;
            }
            if (!checker.test(process)) {
                return false;
            }
            modifier.accept(process);
            try {
                elastic.tryUpdate(process);
                process1stLevelCache.put(processId, process);

                // Trigger a flush of the process ID on every node so the change will be reflected
                process2ndLevelCache.remove(processId);

                process2ndLevelCache.put(processId, process);
                return true;
            } catch (OptimisticLockException _) {
                Wait.randomMillis(250, 500);
                process = elastic.find(Process.class, processId).orElse(null);
            } catch (IntegrityConstraintFailedException exception) {
                Exceptions.handle(Log.BACKGROUND, exception);
                return false;
            }
        }

        Log.BACKGROUND.WARN("Failed to update process %s after 5 attempts. Skipping update...", processId);
        return false;
    }

    /**
     * Updates the state of the given process.
     *
     * @param processId the process to update
     * @param newState  the new state of the process
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */
    protected boolean updateState(String processId, ProcessState newState) {
        return modify(processId, process -> true, process -> process.setState(newState));
    }

    /**
     * Marks a process as running.
     *
     * @param processId the process to update
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */
    protected boolean markRunning(String processId) {
        return modify(processId, process -> process.getState() == ProcessState.WAITING, process -> {
            process.setStarted(LocalDateTime.now());
            process.setWaitingTime((int) Duration.between(process.getCreated(), process.getStarted()).getSeconds());
            process.setState(ProcessState.RUNNING);
        });
    }

    /**
     * Marks a process as canceled.
     * <p>
     * Note that this also marks the process as {@link Process#ERRORNEOUS}.
     *
     * @param processId the process to update
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */
    protected boolean markCanceled(String processId) {
        return modify(processId, process -> {
            return process.getState() == ProcessState.WAITING || process.getState() == ProcessState.RUNNING;
        }, process -> {
            process.setErrorneous(true);
            process.setCanceled(LocalDateTime.now());
            process.setState(ProcessState.CANCELED);
            process.setExpires(process.getPersistencePeriod().plus(LocalDate.now()));
        });
    }

    /**
     * Marks a process as erroneous.
     *
     * @param processId the process to update
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */
    protected boolean markErroneous(String processId) {
        return modify(processId,
                      process -> !process.isErrorneous() && process.getState() == ProcessState.RUNNING,
                      process -> {
                          process.setErrorneous(true);
                          process.setWarnings(false);
                      });
    }

    /**
     * Records, that a process has warnings.
     *
     * @param processId the process to update
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */
    protected boolean markWarnings(String processId) {
        return modify(processId,
                      process -> !process.isErrorneous()
                                 && !process.isWarnings()
                                 && process.getState() == ProcessState.RUNNING,
                      process -> process.setWarnings(true));
    }

    /**
     * Marks a process as erroneous.
     *
     * @param processId        the process to update
     * @param debuggingEnabled determines if debugging should be enabled or disabled
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */
    protected boolean changeDebugging(String processId, boolean debuggingEnabled) {
        return modify(processId,
                      process -> process.getState() == ProcessState.WAITING
                                 || process.getState() == ProcessState.RUNNING
                                 || process.getState() == ProcessState.STANDBY,
                      process -> process.setDebugging(debuggingEnabled));
    }

    /**
     * Changes the persistence period of a process.
     *
     * @param processId         the process to update
     * @param persistencePeriod specifies the new persistence period
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */
    public boolean updatePersistence(String processId, PersistencePeriod persistencePeriod) {
        return modify(processId, process -> process.getPersistencePeriod() != persistencePeriod, process -> {
            PersistencePeriod currentPersistence = process.getPersistencePeriod();
            process.setPersistencePeriod(persistencePeriod);

            LocalDate expires = process.getExpires();
            if (expires != null) {
                expires = currentPersistence.minus(expires);
                expires = persistencePeriod.plus(expires);
                process.setExpires(expires);
            }
        });
    }

    /**
     * Marks a process as completed.
     *
     * @param processId                the process to update
     * @param timings                  timings which have been collected and not yet committed
     * @param adminTimings             timings which have been collected and not yet committed and only administrators should see
     * @param computationTimeInSeconds the computation time of the last step being recorded for this process
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */
    protected boolean markCompleted(String processId,
                                    @Nullable Map<String, Average> timings,
                                    @Nullable Map<String, Average> adminTimings,
                                    int computationTimeInSeconds) {
        return modify(processId, process -> process.getState() != ProcessState.TERMINATED, process -> {
            if (process.getState() != ProcessState.STANDBY) {
                process.setErrorneous(process.isErrorneous() || !TaskContext.get().isActive());
                //Do not alter the job state if the job was previously cancelled by the user
                if (process.getState() != ProcessState.CANCELED) {
                    process.setState(ProcessState.TERMINATED);
                    process.setCompleted(LocalDateTime.now());
                }
                process.setComputationTime(process.getComputationTime() + computationTimeInSeconds);
                process.setExpires(process.getPersistencePeriod().plus(LocalDate.now()));
            }

            if (timings != null) {
                timings.forEach(process::addTiming);
            }

            if (adminTimings != null) {
                adminTimings.forEach(process::addAdminTiming);
            }
        });
    }

    /**
     * Updates the performance counters of the given process.
     *
     * @param processId    the process to update
     * @param timings      the timings (label, value) to store
     * @param adminTimings the set of timings that should only be visible to system tenant users
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */
    protected boolean addTimings(String processId, Map<String, Average> timings, Map<String, Average> adminTimings) {
        return modify(processId, process -> process.getState() != ProcessState.TERMINATED, process -> {
            timings.forEach(process::addTiming);
            adminTimings.forEach(process::addAdminTiming);
        });
    }

    /**
     * Specifies the state message of the given process.
     *
     * @param processId the process to update
     * @param state     the state message to set
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */
    protected boolean setStateMessage(String processId, String state) {
        return modify(processId,
                      process -> process.getState() != ProcessState.TERMINATED,
                      process -> process.setStateMessage(state));
    }

    /**
     * Updates the title of the given process.
     *
     * @param processId the process to update
     * @param newTitle  the title to set
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */
    protected boolean updateTitle(String processId, String newTitle) {
        return modify(processId,
                      process -> process.getState() != ProcessState.TERMINATED && Strings.isFilled(newTitle),
                      process -> process.setTitle(newTitle));
    }

    /**
     * Adds the given link to the given process.
     * <p>
     * When running inside a process the preferred way to add a link is using
     * {@link ProcessContext#addLink(ProcessLink)}. This method is only made public so that outside helper classes
     * can contribute to the process.
     *
     * @param processId the process to update
     * @param link      the link to add
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */
    public boolean addLink(String processId, ProcessLink link) {
        return modify(processId, process -> true, process -> process.getLinks().add(link));
    }

    /**
     * Adds the given link to the given process if it is not already present, based on the link's
     * {@link ProcessLink#equals(Object) equals(Object)} method.
     * <p>
     * When running inside a process the preferred way to add a link is using
     * {@link ProcessContext#addUniqueLink(ProcessLink)}. This method is only made public so that outside helper classes
     * can contribute to the process.
     *
     * @param processId the process to update
     * @param link      the link to add
     * @return <tt>true</tt> if the process was successfully modified or if the link was present before, <tt>false</tt>
     * otherwise
     */
    public boolean addUniqueLink(String processId, ProcessLink link) {
        return modify(processId, process -> true, process -> {
            if (!process.getLinks().contains(link)) {
                process.getLinks().add(link);
            }
        });
    }

    /**
     * Clears all links from the given process.
     * <p>
     * When running inside a process the preferred way to add a link is using
     * {@link ProcessContext#clearLinks()}. This method is only made public so that outside helper classes
     * can contribute to the process.
     *
     * @param processId the process to update
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */
    public boolean clearLinks(String processId) {
        return modify(processId, process -> true, process -> process.getLinks().clear());
    }

    /**
     * Adds the given reference to the given process.
     * <p>
     * When running inside a process the preferred way to add a reference is using
     * {@link ProcessContext#addReference(String)}. This method is only made public so that outside helper classes
     * can contribute to the process.
     *
     * @param processId the process to update
     * @param reference the reference to attach
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */
    public boolean addReference(String processId, String reference) {
        return modify(processId, process -> true, process -> {
            if (!process.getReferences().contains(reference)) {
                process.getReferences().add(reference);
            }
        });
    }

    /**
     * Adds the given output to the given process.
     * <p>
     * When running inside a process the preferred way to add an output is using
     * {@link ProcessContext#addOutput(ProcessOutput)}. This method is only made public so that outside helper classes
     * can contribute to the process.
     *
     * @param processId the process to update
     * @param output    the output to add
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */
    public boolean addOutput(String processId, ProcessOutput output) {
        return modify(processId,
                      process -> process.getState() != ProcessState.TERMINATED,
                      process -> process.getOutputs().add(output));
    }

    /**
     * Adds a file to the given process.
     * <p>
     * When running inside a process the preferred way to add a file is using
     * {@link ProcessContext#addFile(String, File)}. This method is only made public so that outside helper classes
     * can contribute to the process.
     *
     * @param processId the process to update
     * @param filename  the filename to use
     * @param data      the data to persist
     * @return <tt>true</tt> if the process was successfully modified, <tt>false</tt> otherwise
     */
    public boolean addFile(String processId, String filename, File data) {
        Process process = fetchRequiredProcess(processId);

        process.getFiles().findOrCreateAttachedBlobByName(filename).updateContent(filename, data);
        return true;
    }

    /**
     * Adds a file to the given process by providing an {@link OutputStream}.
     * <p>
     * When running inside a process the preferred way to add a file is using
     * {@link ProcessContext#addFile(String)}. This method is only made public so that outside helper classes
     * can contribute to the process.
     *
     * @param processId the process to update
     * @param filename  the filename to use
     * @return the output stream which can be used to provide content for the file to add
     */
    public OutputStream addFile(String processId, String filename) {
        Process process = fetchRequiredProcess(processId);

        return process.getFiles().findOrCreateAttachedBlobByName(filename).createOutputStream(filename);
    }

    /**
     * Returns an input stream to a file stored in the process.
     * <p>
     * Note that it is the responsibility of the caller to close the stream upon usage.
     *
     * @param processId the process to retrieve the file from
     * @param filename  the file name to lookup
     * @return an {@link InputStream} to the file or <tt>null</tt> if none was found
     */
    public InputStream getFile(String processId, String filename) {
        Process process = fetchRequiredProcess(processId);

        return process.getFiles().findAttachedBlobByName(filename).map(Blob::createInputStream).orElse(null);
    }

    /**
     * Stores the log entry for the given process.
     * <p>
     * When running inside a process the preferred way of logging a message is using
     * {@link ProcessContext#log(ProcessLog)}. This method is only made public so that outside helper classes
     * can contribute to the process.
     * <p>
     * Note that this will be done asynchronously to permit bulk inserts.
     *
     * @param processId the process to store the entry for
     * @param logEntry  the entry to persist
     * @see ProcessContext#log(ProcessLog)
     * @see ProcessContext#log(String)
     * @see ProcessContext#logLimited(Object)
     * @see ProcessContext#smartLogLimited(Supplier)
     */
    public void log(String processId, ProcessLog logEntry) {
        try {
            if (logEntry.getType() == ProcessLogType.ERROR) {
                markErroneous(processId);
            } else if (logEntry.getType() == ProcessLogType.WARNING) {
                markWarnings(processId);
            }

            logEntry.setNode(CallContext.getNodeName());
            logEntry.setTimestamp(LocalDateTime.now());
            logEntry.setSortKey(computeSortKey());
            logEntry.getProcess().setId(processId);
            logEntry.getDescriptor().beforeSave(logEntry);

            // Use the auto batch to perform bulk inserts if possible
            if (!autoBatch.insertAsync(logEntry)) {
                // but fallback to regular inserts if the auto batch loop is overloaded due to peak
                // conditions This should automatically slow down the caller and let the auto batch loop
                // recover in parallel...
                elastic.override(logEntry);
            }
        } catch (Exception exception) {
            Exceptions.handle()
                      .withSystemErrorMessage("Failed to record a ProcessLog: %s - %s (%s)", logEntry)
                      .error(exception)
                      .to(Log.BACKGROUND)
                      .handle();
        }
    }

    /**
     * Tries to compute a monotonically increasing message number for each {@link ProcessLog} being recorded.
     * <p>
     * We cannot simply use the {@code System.currentTimeMillis()} call here, as in tight loops, we might
     * insert several logs within one millisecond (as these inserts are lazily batched, this will work out).
     * <p>
     * Therefore, we try to combine {@link System#currentTimeMillis()} with {@link System#nanoTime()}. The former
     * is a "realtime" clock, but only updated each couple of milliseconds, the latter is a system timer which is
     * increased constantly, but its absolute value is somewhat random. Therefore, we use the "whole second" part
     * of the {@link System#currentTimeMillis()} call, to get a stable offset, turn this into microseconds and then
     * add the microsecond offset of {@link System#nanoTime()} to it. This should yield a strictly monotonically
     * increasing counter if it isn't called more than once per microsecond (which is sort of unlikely in Java).
     */
    private Long computeSortKey() {
        //              ┌───────────────────────────────┐              ┌────────────────────────────────┐
        //          ┌──▶│Current system time in seconds │          ┌──▶│System timing counter in micros │
        //          │   └───────────────────────────────┘          │   └────────────────────────────────┘
        //     ─────┴────────────────────────────               ───┴───────────────────────
        return System.currentTimeMillis() / 1_000 * 1_000_000 + (System.nanoTime() / 1_000) % 1_000_000;
        //     ──────────┬───────────────────────────────────   ──┬─────────────────────────────────────
        //               │    ┌──────────────────────────────┐    │   ┌────────────────────────────────────────┐
        //               └───▶│Current system time in micros │    └──▶│ System timing counter offset in micros │
        //                    └──────────────────────────────┘        └────────────────────────────────────────┘
    }

    protected long countMessagesForType(String processId, String messageType) {
        return elastic.select(ProcessLog.class)
                      .eq(ProcessLog.PROCESS, processId)
                      .eq(ProcessLog.MESSAGE_TYPE, messageType)
                      .count();
    }

    protected void reportLimitedMessages(String processId,
                                         Map<String, AtomicInteger> messageCountsPerType,
                                         Map<String, Integer> limitsPerType) {
        for (Map.Entry<String, AtomicInteger> entry : messageCountsPerType.entrySet()) {
            String type = entry.getKey();
            int limit = limitsPerType.getOrDefault(type, Integer.MAX_VALUE);
            int count = entry.getValue().intValue();
            if (count > limit) {
                log(processId,
                    ProcessLog.warn()
                              .withMessageType(entry.getKey())
                              .withNLSKey("Processes.messageLimitReached")
                              .withContext("type", NLS.smartGet(type))
                              .withContext("limit", limit)
                              .withContext("count", count));
            }
        }
    }

    protected boolean awaitFlushedLogs(String processId) {
        if (!autoBatch.awaitNextFlush(Duration.ofSeconds(10))) {
            log(processId,
                ProcessLog.error()
                          .withMessage("Failed to wait for logs to be flushed. Some reports might be incomplete!"));
            return false;
        }

        // Even after a batch insert, we still should give ES some time to digest the data...
        Wait.seconds(2);

        return true;
    }

    /**
     * Executes the given task "within" the given process.
     *
     * @param processId the process to execute within
     * @param task      the task to execute
     * @param complete  <tt>true</tt> to mark the process as completed once the task is done, <tt>false</tt> otherwise
     * @throws sirius.kernel.health.HandledException in case of an error which occurred while executing the task
     */
    @SuppressWarnings("java:S2440")
    @Explain("This is a false positive as this is our execution environment, not the one of Java")
    private void execute(String processId, Consumer<ProcessContext> task, boolean complete) {
        awaitProcess(processId);
        TaskContext taskContext = TaskContext.get();
        UserContext userContext = UserContext.get();

        TaskContextAdapter taskContextAdapterBackup = taskContext.getAdapter();
        UserInfo userInfoBackup = userContext.getUser();

        Watch watch = Watch.start();
        ProcessEnvironment environment = new ProcessEnvironment(processId);
        taskContext.setJob(processId);
        taskContext.setAdapter(environment);
        try {
            if (environment.isActive()) {
                CallContext.getCurrent().resetLanguage();
                installUserOfProcess(userContext, environment);

                task.accept(environment);
            }
        } catch (Exception exception) {
            throw environment.handle(exception);
        } finally {
            environment.awaitSideTaskCompletion();
            CallContext.getCurrent().resetLanguage();
            taskContext.setAdapter(taskContextAdapterBackup);
            userContext.setCurrentUser(userInfoBackup);

            int computationTimeInSeconds = (int) watch.elapsed(TimeUnit.SECONDS, false);
            if (complete) {
                environment.markCompleted(computationTimeInSeconds);
            } else {
                modify(processId,
                       process -> process.getState() != ProcessState.STANDBY || computationTimeInSeconds >= 10,
                       process -> process.setComputationTime(process.getComputationTime() + computationTimeInSeconds));
                environment.flushTimings();
            }
        }
    }

    /**
     * Waits until the process really exists.
     * <p>
     * As {@link #createProcess(String, String, String, UserInfo, PersistencePeriod, Map)} performs an insert into ES
     * without any delay, the same process might not yet be visible on another node (due to the 1s insert delay of ES).
     * Therefore, we check the existence of the process and wait a certain amount of time if it doesn't exist.
     * <p>
     * Note that this isn't necessary on the same node and therefore actually bypassed, as the 1st level
     * cache will be properly populated and therefore this check will immediately succeed.
     *
     * @param processId the process to check
     */
    private void awaitProcess(String processId) {
        int attempts = 4;
        while (attempts-- > 0) {
            if (fetchProcess(processId).isPresent()) {
                return;
            }
            Wait.millis(300);
        }

        throw new IllegalStateException("Unknown process id: " + processId);
    }

    /**
     * Installs the user defined by the process (into the {@link UserContext}).
     * <p>
     * If no user is attached to the process, no modification will be performed.
     *
     * @param userContext the context to update
     * @param environment the process environment to read the user infos from
     */
    private void installUserOfProcess(UserContext userContext, ProcessEnvironment environment) {
        String userId = environment.fetchUserId();
        String tenantId = environment.fetchTenantId();
        String tenantName = environment.fetchTenantName();

        if (Strings.isEmpty(userId)) {
            return;
        }

        if (Strings.areEqual(userId, UserInfo.SYNTHETIC_ADMIN_USER_ID) && Strings.isFilled(tenantId)) {
            userContext.setCurrentUser(UserInfo.Builder.createSyntheticAdminUser(tenantId, tenantName).build());
            return;
        }

        UserInfo user = userContext.getUserManager().findUserByUserId(userId);
        if (user != null) {
            user = userContext.getUserManager().createUserWithTenant(user, tenantId);
            userContext.setCurrentUser(user);
        }
    }

    /**
     * Executes the given task in the given process without marking it as {@link ProcessState#TERMINATED}.
     *
     * @param processId the process to execute the task in
     * @param task      the task to execute
     * @throws sirius.kernel.health.HandledException in case of an error which occurred while executing the task
     */
    public void partiallyExecute(String processId, Consumer<ProcessContext> task) {
        execute(processId, task, false);
    }

    /**
     * Executes the given task in the given process and then marks it as {@link ProcessState#TERMINATED}.
     *
     * @param processId the process to execute the task in
     * @param task      the task to execute
     * @throws sirius.kernel.health.HandledException in case of an error which occurred while executing the task
     */
    public void execute(String processId, Consumer<ProcessContext> task) {
        execute(processId, task, true);
    }

    /**
     * Determines if the current user has any active processes.
     *
     * @return <tt>true</tt> if there are any active processes visible for the current user, <tt>false</tt> otherwise
     */
    public boolean hasActiveProcesses() {
        try {
            return queryProcessesForCurrentUser().where(elastic.filters()
                                                               .oneInField(Process.STATE,
                                                                           List.of(ProcessState.WAITING,
                                                                                   ProcessState.RUNNING))
                                                               .build()).exists();
        } catch (Exception exception) {
            Exceptions.handle(Log.SYSTEM, exception);
            return false;
        }
    }

    /**
     * Builds a query to obtain all processes visible to the current user.
     *
     * @return a query for all processes visible to the current user
     */
    public ElasticQuery<Process> queryProcessesForCurrentUser() {
        ElasticQuery<Process> query =
                elastic.select(Process.class).orderDesc(Process.CREATED).orderDesc(Process.STARTED);

        UserInfo user = UserContext.getCurrentUser();
        if (!user.hasPermission(ProcessController.PERMISSION_MANAGE_ALL_PROCESSES)) {
            query.eq(Process.TENANT_ID, user.getTenantId());
        }

        if (user.hasPermission(ProcessController.PERMISSION_MANAGE_PROCESSES)) {
            query.where(Elastic.FILTERS.oneInField(Process.REQUIRED_PERMISSION, new ArrayList<>(user.getPermissions()))
                                       .orEmpty()
                                       .build());
        } else {
            query.eq(Process.USER_ID, user.getUserId());
        }

        return query;
    }

    /**
     * Resolves the id into a process while ensuring that the current user may access it.
     * <p>
     * Note that this will not utilize the 1st and 2nd level cache as it is intended for UI (read) access.
     *
     * @param processId the id to resolve into a process
     * @return the resolved process wrapped as optional, or an empty optional if there is no such process
     * or the user may not access it.
     */
    public Optional<Process> fetchProcessForUser(String processId) {
        Optional<Process> process = elastic.find(Process.class, processId);
        if (process.isEmpty()) {
            // Maybe the given process id was just created and not visible in ES.
            // Wait for a good second and retry once...
            Wait.millis(1200);
            process = elastic.find(Process.class, processId);
            if (process.isEmpty()) {
                return Optional.empty();
            }
        }

        UserInfo user = UserContext.getCurrentUser();
        if (!Objects.equals(user.getTenantId(), process.get().getTenantId())
            && !user.hasPermission(ProcessController.PERMISSION_MANAGE_ALL_PROCESSES)) {
            return Optional.empty();
        }

        if (!Strings.areEqual(user.getUserId(), process.get().getUserId())
            && !user.hasPermission(ProcessController.PERMISSION_MANAGE_PROCESSES)) {
            return Optional.empty();
        }

        if (!user.hasPermission(process.get().getRequiredPermission())) {
            return Optional.empty();
        }

        return process;
    }

    /**
     * Resolves the id into a log entry while ensuring that the current user may access it.
     *
     * @param processLogId the id to resolve into a log entry
     * @return the resolved log entry wrapped as optional or an empty optional if there is no such log entry
     * or the user may not access it.
     */
    public Optional<ProcessLog> fetchProcessLogForUser(String processLogId) {
        Optional<ProcessLog> processLog = elastic.find(ProcessLog.class, processLogId);
        if (processLog.isEmpty()) {
            return Optional.empty();
        }
        if (fetchProcessForUser(processLog.get().getProcess().getId()).isEmpty()) {
            return Optional.empty();
        }

        return processLog;
    }

    /**
     * Updates the state of the given process log entry and returns to the given URL be redirecting the given request.
     * <p>
     * Note that this will also emit an {@link sirius.biz.protocol.JournalEntry} to record that the current user
     * changed the state of the given log entry.
     * <p>
     * Also note, that this method doesn't perform any access checks, therefore the caller has to ensure that the
     * current user may modify the given log entry.
     *
     * @param processLog the log entry to modify
     * @param newState   the new state to set
     * @param webContext the request to respond to
     * @param returnUrl  the URL to redirect the request to once the modification has been performed and is visible
     */
    public void updateProcessLogStateAndReturn(ProcessLog processLog,
                                               ProcessLogState newState,
                                               WebContext webContext,
                                               String returnUrl) {
        processLog.withState(newState);
        elastic.update(processLog);
        JournalData.addJournalEntry(processLog, NLS.get("ProcessLog.state") + ": " + newState.toString());
        delayLine.forkDelayed(Tasks.DEFAULT, 1, () -> webContext.respondWith().redirectToGet(returnUrl));
    }

    protected Optional<ProcessOutput> fetchOutput(String processId, String outputName) {
        return fetchProcess(processId).flatMap(process -> process.getOutputs()
                                                                 .data()
                                                                 .stream()
                                                                 .filter(output -> Strings.areEqual(output.getName(),
                                                                                                    outputName))
                                                                 .findFirst());
    }

    protected void fetchOutputEntries(String processId,
                                      String outputName,
                                      BiConsumer<List<String>, List<String>> columnsAndLabelsConsumer,
                                      BiPredicate<List<String>, List<String>> columnsAndValues) {
        Process process = fetchRequiredProcess(processId);

        if (Strings.isEmpty(outputName)) {
            fetchOutputLogs(process, null, columnsAndLabelsConsumer, columnsAndValues);
            return;
        }

        ProcessOutput processOutput = process.getOutputs()
                                             .data()
                                             .stream()
                                             .filter(output -> Strings.areEqual(output.getName(), outputName))
                                             .findFirst()
                                             .orElseThrow(() -> new IllegalArgumentException(Strings.apply(
                                                     "Unknown output (%s) requested for process %s",
                                                     outputName,
                                                     processId)));

        if (TableProcessOutputType.TYPE.equals(processOutput.getType())) {
            fetchOutputTable(process, processOutput, columnsAndLabelsConsumer, columnsAndValues);
        } else if (LogsProcessOutputType.TYPE.equals(processOutput.getType())) {
            fetchOutputLogs(process, processOutput, columnsAndLabelsConsumer, columnsAndValues);
        } else {
            throw new IllegalArgumentException(Strings.apply(
                    "Exporting process outputs is only supported for logs and tables, not %s (of output %s of process %s)",
                    processOutput.getType(),
                    processOutput.getName(),
                    processId));
        }
    }

    private void fetchOutputLogs(Process process,
                                 ProcessOutput processOutput,
                                 BiConsumer<List<String>, List<String>> columnsAndLabelsConsumer,
                                 BiPredicate<List<String>, List<String>> columnsAndValues) {
        List<String> columns = Arrays.asList("type", "timestamp", "message", "messageType", "node");
        List<String> labels = Arrays.asList(NLS.get("ProcessLog.type"),
                                            NLS.get("ProcessLog.timestamp"),
                                            NLS.get("ProcessLog.message"),
                                            NLS.get("ProcessLog.messageType"),
                                            NLS.get("ProcessLog.node"));

        columnsAndLabelsConsumer.accept(columns, labels);
        createLogsQuery(processOutput, process).iterate(logEntry -> {
            List<String> values = Arrays.asList(logEntry.getType().toString(),
                                                NLS.toMachineString(logEntry.getTimestamp()),
                                                logEntry.getMessage(),
                                                NLS.smartGet(logEntry.getMessageType()),
                                                logEntry.getNode());
            return columnsAndValues.test(columns, values);
        });
    }

    private void fetchOutputTable(Process process,
                                  ProcessOutput processOutput,
                                  BiConsumer<List<String>, List<String>> columnsAndLabelsConsumer,
                                  BiPredicate<List<String>, List<String>> columnsAndValues) {
        List<String> columns = tableProcessOutputType.determineColumns(processOutput);
        List<String> labels = tableProcessOutputType.determineLabels(processOutput, columns);

        columnsAndLabelsConsumer.accept(columns, labels);
        createLogsQuery(processOutput, process).iterate(logEntry -> {
            List<String> values = columns.stream()
                                         .map(column -> cells.rawValue(logEntry.getContext().get(column).orElse(null)))
                                         .toList();
            return columnsAndValues.test(columns, values);
        });
    }

    private ElasticQuery<ProcessLog> createLogsQuery(@Nullable ProcessOutput out, Process process) {
        ElasticQuery<ProcessLog> logsQuery = elastic.select(ProcessLog.class)
                                                    .eq(ProcessLog.PROCESS, process)
                                                    .eq(ProcessLog.OUTPUT, out != null ? out.getName() : null)
                                                    .orderAsc(ProcessLog.SORT_KEY);
        UserInfo user = UserContext.getCurrentUser();
        if (!user.hasPermission(ProcessController.PERMISSION_MANAGE_ALL_PROCESSES)) {
            logsQuery.eq(ProcessLog.SYSTEM_MESSAGE, false);
        }

        return logsQuery;
    }

    /**
     * Obtains some process metrics for the given period.
     *
     * @param startOfPeriod the start date of the period to query
     * @param endOfPeriod   the end date of the period to query
     * @param tenant        the tenant to filter on (if present)
     * @return a tuple containing the number of processes and the total execution time in minutes. Note that we
     * collect all processes which {@link Process#COMPLETED} date is within the given period.
     */
    public Tuple<Integer, Integer> computeProcessMetrics(LocalDate startOfPeriod,
                                                         LocalDate endOfPeriod,
                                                         @Nullable Tenant<?> tenant) {
        AtomicInteger numProcesses = new AtomicInteger(0);
        AtomicLong computationDurationSeconds = new AtomicLong(0);
        elastic.select(Process.class)
               .where(Elastic.FILTERS.gte(Process.COMPLETED, startOfPeriod.atStartOfDay()))
               .where(Elastic.FILTERS.lt(Process.COMPLETED, endOfPeriod.plusDays(1).atStartOfDay()))
               .eq(Process.STATE, ProcessState.TERMINATED)
               .eqIgnoreNull(Process.TENANT_ID, tenant != null ? tenant.getIdAsString() : null)
               .streamBlockwise()
               .forEach(process -> {
                   numProcesses.incrementAndGet();
                   computationDurationSeconds.addAndGet(process.getComputationTime());
               });

        return Tuple.create(numProcesses.get(), (int) TimeUnit.SECONDS.toMinutes(computationDurationSeconds.get()));
    }

    /**
     * Fetches a list of processes which hold a reference to the provided {@linkplain BaseEntity base entity}.
     *
     * @param baseEntity the entity for which all associated processes shall be fetched
     * @return a list of processes who are associated with the provided entity
     */
    public List<Process> fetchAssociatedProcesses(BaseEntity<?> baseEntity) {
        return queryProcessesForCurrentUser().eq(Process.REFERENCES, baseEntity.getUniqueName()).limit(5).queryList();
    }
}
