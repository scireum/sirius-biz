/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.work;

import sirius.db.redis.Redis;
import sirius.kernel.Sirius;
import sirius.kernel.async.BackgroundLoop;
import sirius.kernel.async.CallContext;
import sirius.kernel.async.Orchestration;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.di.Initializable;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Parts;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;
import sirius.kernel.timer.EveryDay;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Register
public class NeighborhoodWatch implements Orchestration, Initializable {

    private static final Log LOG = Log.get("orchestration");
    private static final String EXECUTION_TIMESTAMP_SUFFIX = "-timestamp";
    private static final String EXECUTION_ENABLED_SUFFIX = "-enabled";
    private static final String EXECUTION_INFO_SUFFIX = "-info";
    private static final String BACKGROUND_LOOP_PREFIX = "loop-";
    private static final String DAILY_TASK_PREFIX = "task-";

    public enum SynchronizeType {
        DISABLED, CLUSTER, LOCAL
    }

    @Part
    private Redis redis;

    @Parts(BackgroundLoop.class)
    private Collection<BackgroundLoop> loops;

    @Parts(EveryDay.class)
    private Collection<EveryDay> dailyTasks;

    private Map<String, SynchronizeType> syncSettings = Collections.emptyMap();
    private Map<String, Tuple<Boolean, Long>> globallyEnabledState = new HashMap<>();

    @Override
    public boolean tryExecuteBackgroundLoop(String name) {
        String syncName = BACKGROUND_LOOP_PREFIX + name;
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

    public boolean isBackgroundJobGloballyEnabled(String name) {
        try {
            if (!redis.isConfigured()) {
                return true;
            }

            Tuple<Boolean, Long> globalState = globallyEnabledState.get(name);
            if (globalState != null
                && Duration.ofMillis(System.currentTimeMillis() - globalState.getSecond()).getSeconds() < 30) {
                return globalState.getFirst();
            }

            String value = redis.query(() -> "Check if " + name + " is enabled",
                                       db -> db.get(name + EXECUTION_ENABLED_SUFFIX));

            boolean enabled = !"disabled".equals(value);
            globallyEnabledState.put(name, Tuple.create(enabled, System.currentTimeMillis()));

            return enabled;
        } catch (Exception e) {
            Exceptions.handle(LOG, e);
            return true;
        }
    }

    public boolean isBackgroundJobLocallyEnabled(String name) {
        SynchronizeType type = syncSettings.getOrDefault(name, SynchronizeType.LOCAL);
        return type != SynchronizeType.DISABLED;
    }

    @Override
    public void backgroundLoopCompleted(String name, String executionInfo) {
        try {
            if (CallContext.getCurrent().get(TaskContext.class).isActive() && redis.isConfigured()) {
                String syncName = BACKGROUND_LOOP_PREFIX + name;
                SynchronizeType type = syncSettings.getOrDefault(syncName, SynchronizeType.LOCAL);
                if (type == SynchronizeType.CLUSTER) {
                    redis.unlock(syncName);
                }

                redis.exec(() -> "Write execution info " + syncName, db -> {
                    db.setex(syncName + EXECUTION_INFO_SUFFIX,
                             26 * 60 * 60,
                             CallContext.getNodeName() + ": " + executionInfo);
                });
            }
        } catch (Exception e) {
            Exceptions.handle(LOG, e);
        }
    }

    @Override
    public boolean shouldRunDailyTask(String name) {
        String syncName = DAILY_TASK_PREFIX + name;

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
            return shouldRunClusteredDailyTask(syncName);
        } else {
            return true;
        }
    }

    private boolean shouldRunClusteredDailyTask(String syncName) {
        try {
            String lastExecution = redis.query(() -> "Read last execution of " + syncName,
                                               db -> db.get(syncName + EXECUTION_TIMESTAMP_SUFFIX));
            long lastExecutionMillis = Value.of(lastExecution).asLong(0);
            if (Duration.ofMillis(System.currentTimeMillis() - lastExecutionMillis).toHours() < 12) {
                return false;
            }
            if (!redis.tryLock(syncName, null, Duration.ofMinutes(1))) {
                return false;
            }

            try {
                redis.exec(() -> "Write last execution of " + syncName, db -> {
                    db.setex(syncName + EXECUTION_TIMESTAMP_SUFFIX,
                             26 * 60 * 60,
                             String.valueOf(System.currentTimeMillis()));
                    db.setex(syncName + EXECUTION_INFO_SUFFIX,
                             26 * 60 * 60,
                             CallContext.getNodeName() + ": " + NLS.toUserString(LocalDateTime.now()));
                });
                return true;
            } finally {
                redis.unlock(syncName);
            }
        } catch (Exception e) {
            Exceptions.handle(LOG, e);
        }

        return false;
    }

    @Override
    public void initialize() throws Exception {
        Map<String, SynchronizeType> targetMap = new HashMap<>();
        StringBuilder stateBuilder = new StringBuilder();
        stateBuilder.append("\n\nNeighborhood Watch is ONLINE\n");
        stateBuilder.append("----------------------------\n");

        stateBuilder.append("\nBackground Loops\n");
        stateBuilder.append("----------------------------\n");
        for (BackgroundLoop loop : loops) {
            String key = BACKGROUND_LOOP_PREFIX + loop.getName();
            loadAndStoreSetting(key, targetMap);
            stateBuilder.append(key).append(": ").append(targetMap.get(key)).append("\n");
        }

        stateBuilder.append("\nDaily Tasks\n");
        stateBuilder.append("----------------------------\n");
        for (EveryDay task : dailyTasks) {
            String key = DAILY_TASK_PREFIX + task.getConfigKeyName();
            loadAndStoreSetting(key, targetMap);
            stateBuilder.append(key).append(": ").append(targetMap.get(key)).append("\n");
        }

        syncSettings = targetMap;
        LOG.INFO(stateBuilder.toString());
    }

    private void loadAndStoreSetting(String key, Map<String, SynchronizeType> targetMap) {
        if (!Sirius.getSettings().getConfig().hasPath("orchestration." + key)) {
            LOG.WARN("No configuration found for orchestration." + key);
            targetMap.put(key, SynchronizeType.LOCAL);
            return;
        }

        Value setting = Sirius.getSettings().get("orchestration." + key);
        try {
            targetMap.put(key, SynchronizeType.valueOf(setting.toUpperCase()));
        } catch (IllegalArgumentException e) {
            LOG.WARN("Invalid configuration found for orchestration." + key + ": " + setting.toString());
            targetMap.put(key, SynchronizeType.LOCAL);
        }
    }
}
