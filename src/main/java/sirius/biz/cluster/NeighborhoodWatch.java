/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster;

import com.alibaba.fastjson.JSONObject;
import sirius.db.redis.Redis;
import sirius.kernel.Sirius;
import sirius.kernel.async.BackgroundLoop;
import sirius.kernel.async.CallContext;
import sirius.kernel.async.Orchestration;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Lambdas;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.di.Initializable;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Parts;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.kernel.timer.EveryDay;
import sirius.web.health.Cluster;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Register(classes = {Orchestration.class, NeighborhoodWatch.class, Initializable.class})
public class NeighborhoodWatch implements Orchestration, Initializable {

    private static final String EXECUTION_TIMESTAMP_SUFFIX = "-timestamp";
    private static final String EXECUTION_ENABLED_SUFFIX = "-enabled";
    private static final String BACKGROUND_LOOP_PREFIX = "loop-";
    private static final String DAILY_TASK_PREFIX = "task-";
    public static final String QUEUE_PREFIX = "queue-";

    private static final Duration MAX_GLOBAL_STATE_CACHE_DURATION = Duration.ofSeconds(32);
    private static final int MIN_WAIT_DAILY_TASK_HOURS = 10;

    @Part
    private Redis redis;

    @Part
    private InterconnectClusterManager clusterManager;

    @Parts(BackgroundLoop.class)
    private Collection<BackgroundLoop> loops;

    @Parts(EveryDay.class)
    private Collection<EveryDay> dailyTasks;

    private Map<String, SynchronizeType> syncSettings = Collections.emptyMap();
    private Map<String, Boolean> localOverwrite = new ConcurrentHashMap<>();
    private Map<String, String> executionInfos = new ConcurrentHashMap<>();
    private Map<String, Tuple<Boolean, LocalDateTime>> globallyEnabledState = new ConcurrentHashMap<>();

    private boolean isBackgroundJobGloballyEnabled(String name) {
        try {
            if (!redis.isConfigured()) {
                return true;
            }

            Tuple<Boolean, LocalDateTime> globalState = globallyEnabledState.get(name);
            if (globalState != null
                && Duration.between(LocalDateTime.now(), globalState.getSecond())
                           .compareTo(MAX_GLOBAL_STATE_CACHE_DURATION) < 0) {
                return globalState.getFirst();
            }

            String value = redis.query(() -> "Check if " + name + " is enabled",
                                       db -> db.get(name + EXECUTION_ENABLED_SUFFIX));

            boolean enabled = !"disabled".equals(value);
            globallyEnabledState.put(name, Tuple.create(enabled, LocalDateTime.now()));

            return enabled;
        } catch (Exception e) {
            Exceptions.handle(Cluster.LOG, e);
            return true;
        }
    }

    @Override
    public boolean tryExecuteBackgroundLoop(String name) {
        String syncName = BACKGROUND_LOOP_PREFIX + name;

        if (localOverwrite.containsKey(syncName)) {
            return false;
        }

        SynchronizeType type = syncSettings.getOrDefault(syncName, SynchronizeType.LOCAL);
        if (type == SynchronizeType.DISABLED) {
            return false;
        }

        if (!redis.isConfigured()) {
            return true;
        }

        if (!isBackgroundJobGloballyEnabled(syncName)) {
            return false;
        }

        if (type == SynchronizeType.CLUSTER) {
            return redis.tryLock(syncName, null, Duration.ofMinutes(30));
        } else {
            return true;
        }
    }

    @Override
    public void backgroundLoopCompleted(String name, String executionInfo) {
        try {
            String syncName = BACKGROUND_LOOP_PREFIX + name;
            if (CallContext.getCurrent().get(TaskContext.class).isActive() && redis.isConfigured()) {
                SynchronizeType type = syncSettings.getOrDefault(syncName, SynchronizeType.LOCAL);
                if (type == SynchronizeType.CLUSTER) {
                    redis.unlock(syncName);
                }
            }

            executionInfos.put(syncName, executionInfo);
        } catch (Exception e) {
            Exceptions.handle(Cluster.LOG, e);
        }
    }

    @Override
    public boolean shouldRunDailyTask(String name) {
        String syncName = DAILY_TASK_PREFIX + name;

        if (localOverwrite.containsKey(syncName)) {
            return false;
        }

        SynchronizeType type = syncSettings.getOrDefault(syncName, SynchronizeType.LOCAL);
        if (type == SynchronizeType.DISABLED) {
            return false;
        }

        if (!redis.isConfigured()) {
            executionInfos.put(syncName, "Executed: " + NLS.toUserString(LocalDateTime.now()));

            return true;
        }

        if (!isBackgroundJobGloballyEnabled(syncName)) {
            return false;
        }

        if (type == SynchronizeType.LOCAL || shouldRunClusteredDailyTask(syncName)) {
            executionInfos.put(syncName, "Executed: " + NLS.toUserString(LocalDateTime.now()));
            return true;
        } else {
            return false;
        }
    }

    private boolean shouldRunClusteredDailyTask(String syncName) {
        try {
            String lastExecution = redis.query(() -> "Read last execution of " + syncName,
                                               db -> db.get(syncName + EXECUTION_TIMESTAMP_SUFFIX));
            long lastExecutionMillis = Value.of(lastExecution).asLong(0);
            if (Duration.ofMillis(System.currentTimeMillis() - lastExecutionMillis).toHours()
                < MIN_WAIT_DAILY_TASK_HOURS) {
                return false;
            }

            return redis.query(() -> "Write last execution of " + syncName, db -> {
                long update =
                        db.setnx(syncName + EXECUTION_TIMESTAMP_SUFFIX, String.valueOf(System.currentTimeMillis()));
                if (update == 1L) {
                    db.expire(syncName + EXECUTION_TIMESTAMP_SUFFIX,
                              (int) TimeUnit.HOURS.toSeconds(MIN_WAIT_DAILY_TASK_HOURS));
                    return true;
                } else {
                    return false;
                }
            });
        } catch (Exception e) {
            Exceptions.handle(Cluster.LOG, e);
            return false;
        }
    }

    public boolean isDistributedTaskQueueEnabled(String name) {
        String syncName = QUEUE_PREFIX + name;

        if (localOverwrite.containsKey(syncName)) {
            return false;
        }

        SynchronizeType type = syncSettings.getOrDefault(syncName, SynchronizeType.LOCAL);
        if (type == SynchronizeType.DISABLED) {
            return false;
        }

        if (!redis.isConfigured()) {
            return true;
        }

        return isBackgroundJobGloballyEnabled(syncName);
    }

    @Override
    public void initialize() throws Exception {
        Map<String, SynchronizeType> targetMap = new HashMap<>();
        StringBuilder stateBuilder = new StringBuilder();
        stateBuilder.append("\n\nNeighborhood Watch is ONLINE\n");
        appendSeparator(stateBuilder);

        stateBuilder.append("\nBackground Loops\n");
        appendSeparator(stateBuilder);
        for (BackgroundLoop loop : loops) {
            String key = BACKGROUND_LOOP_PREFIX + loop.getName();
            loadAndStoreSetting(key, targetMap);
            stateBuilder.append(key).append(": ").append(targetMap.get(key)).append("\n");
        }

        stateBuilder.append("\nDaily Tasks\n");
        appendSeparator(stateBuilder);
        for (EveryDay task : dailyTasks) {
            String key = DAILY_TASK_PREFIX + task.getConfigKeyName();
            loadAndStoreSetting(key, targetMap);
            stateBuilder.append(key).append(": ").append(targetMap.get(key)).append("\n");
        }

        syncSettings = targetMap;
        Cluster.LOG.INFO(stateBuilder.toString());
    }

    protected void appendSeparator(StringBuilder stateBuilder) {
        stateBuilder.append("----------------------------\n");
    }

    private void loadAndStoreSetting(String key, Map<String, SynchronizeType> targetMap) {
        if (!Sirius.getSettings().getConfig().hasPath("orchestration." + key)) {
            Cluster.LOG.WARN("No configuration found for orchestration." + key);
            targetMap.put(key, SynchronizeType.LOCAL);
            return;
        }

        Value setting = Sirius.getSettings().get("orchestration." + key);
        try {
            targetMap.put(key, SynchronizeType.valueOf(setting.toUpperCase()));
        } catch (IllegalArgumentException e) {
            Cluster.LOG.WARN("Invalid configuration found for orchestration." + key + ": " + setting.toString());
            targetMap.put(key, SynchronizeType.LOCAL);
        }
    }

    public List<BackgroundInfo> getClusterBackgroundInfo() {
        List<BackgroundInfo> result = new ArrayList<>();
        result.add(getLocalBackgroundInfo());
        clusterManager.callEachNode("/system/cluster/background")
                      .map(this::parseBackgroundInfos)
                      .collect(Lambdas.into(result));

        return result;
    }

    public BackgroundInfo getLocalBackgroundInfo() {
        BackgroundInfo result = new BackgroundInfo(CallContext.getNodeName(),
                                                   NLS.convertDuration(Sirius.getUptimeInMilliseconds(), true, false));

        for (Map.Entry<String, SynchronizeType> job : syncSettings.entrySet()) {
            result.jobs.add(new BackgroundJobInfo(job.getKey(),
                                                  job.getValue(),
                                                  localOverwrite.containsKey(job.getKey()),
                                                  isBackgroundJobGloballyEnabled(job.getKey()),
                                                  executionInfos.getOrDefault(job.getKey(), "")));
        }
        return result;
    }

    private BackgroundInfo parseBackgroundInfos(JSONObject jsonObject) {
        BackgroundInfo result = new BackgroundInfo(jsonObject.getString(InterconnectClusterManager.RESPONSE_NODE_NAME),
                                                   jsonObject.getString(ClusterController.RESPONSE_UPTIME));
        jsonObject.getJSONArray(ClusterController.RESPONSE_JOBS).forEach(job -> {
            try {
                JSONObject jobJson = (JSONObject) job;
                result.jobs.add(new BackgroundJobInfo(jobJson.getString(ClusterController.RESPONSE_NAME),
                                                      SynchronizeType.valueOf(jobJson.getString(ClusterController.RESPONSE_LOCAL)),
                                                      jobJson.getBooleanValue(ClusterController.RESPONSE_LOCAL_OVERWRITE),
                                                      jobJson.getBooleanValue(ClusterController.RESPONSE_GLOBALLY_ENABLED),
                                                      jobJson.getString(ClusterController.RESPONSE_EXECUTION_INFO)));
            } catch (Exception e) {
                Exceptions.handle(Cluster.LOG, e);
            }
        });

        return result;
    }
}
