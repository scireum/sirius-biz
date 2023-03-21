/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jupiter;

import redis.clients.jedis.Connection;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;
import sirius.db.redis.RedisDB;
import sirius.kernel.Sirius;
import sirius.kernel.async.Operation;
import sirius.kernel.commons.Watch;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Microtiming;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Permits to access a <b>Jupiter</b> instance.
 * <p>
 * This connector is in charge of taking of about the connection management. It also permits failing over to
 * a fallback instance in case the main instance isn't reachable.
 * <p>
 * If a failover is performed, we will continue to use the fallback for up to 60s before we attempt to switch
 * back to the main instance.
 * <p>
 * A connector is usually obtained via {@link Jupiter#getConnector(String)} or {@link Jupiter#getDefault()}.
 */
public class JupiterConnector {

    private static final JupiterCommand CMD_SET_CONFIG = new JupiterCommand("SYS.SET_CONFIG");

    /**
     * If a failover is performed, we keep using the fallback instance for a certain amount of time to prevent
     * constant switching back and forth in case of network problems (etc). This constant determines the interval
     * before a failover back to the main instance is attempted.
     */
    private static final int FAILOVER_TRIGGER_REARM_INTERVAL = 60_000;

    /**
     * Provides an upper bound of the expected runtime of a Jupiter command for monitoring purposes.
     */
    private static final Duration EXPECTED_JUPITER_COMMAND_RUNTIME = Duration.ofSeconds(10);

    private final String instanceName;
    private final RedisDB redis;
    private final RedisDB fallbackRedis;
    private final Jupiter jupiter;
    private long fallbackActiveUntil;

    protected JupiterConnector(Jupiter jupiter, String instanceName, RedisDB redis, RedisDB fallbackRedis) {
        this.jupiter = jupiter;
        this.instanceName = instanceName;
        this.redis = redis;
        this.fallbackRedis = fallbackRedis;
    }

    /**
     * Returns the instance (config) name of this Jupiter instance.
     *
     * @return the name of the config instace being used
     */
    public String getName() {
        return instanceName;
    }

    /**
     * Determines if access to Redis is configured.
     *
     * @return <tt>true</tt> if at least a host is available, <tt>false</tt> otherwise
     */
    public boolean isConfigured() {
        return redis.isConfigured();
    }

    /**
     * Returns the list of namespaces which are enabled for this connector.
     *
     * @return the list of enabled namespaces
     */
    public List<String> fetchEnabledNamespaces() {
        return Sirius.getSettings().getExtension("jupiter.settings", getName()).getStringList("repository.namespaces");
    }

    /**
     * Executes one or more commands and returns a value of the given type.
     * <p>
     * If a HA option is present in <tt>jupiter.ha</tt>, the given fallback pool is used in case the main
     * pool isn't available.
     *
     * @param description a description of the actions performed used for debugging and tracing
     * @param task        the actual task to perform using redis
     * @param <T>         the generic type of the result
     * @return a result computed by <tt>task</tt>
     */
    public <T> T query(Supplier<String> description, Function<Connection, T> task) {
        if (fallbackRedis == null) {
            return queryDirect(description, task);
        }

        if (fallbackActiveUntil > 0 && fallbackActiveUntil < System.currentTimeMillis()) {
            fallbackActiveUntil = 0;
            Jupiter.LOG.INFO("Attempting to perform a failover back to the main instance for %s", getName());
        }

        if (fallbackActiveUntil > 0) {
            return performWithFailover(description, task, fallbackRedis, redis, () -> {
                fallbackActiveUntil = 0;
                Jupiter.LOG.WARN("Performing a failover from the fallback to the main instance for %s", getName());
            });
        } else {
            return performWithFailover(description, task, redis, fallbackRedis, () -> {
                fallbackActiveUntil = System.currentTimeMillis() + FAILOVER_TRIGGER_REARM_INTERVAL;
                Jupiter.LOG.WARN("Performing a failover from the main instance to the fallback for %s", getName());
            });
        }
    }

    private <T> T performWithFailover(Supplier<String> description,
                                      Function<Connection, T> task,
                                      RedisDB main,
                                      RedisDB fallback,
                                      Runnable executeOnFailover) {
        try (Operation op = new Operation(description, EXPECTED_JUPITER_COMMAND_RUNTIME);
             Jedis jedis = main.getConnection()) {
            return perform(description, jedis, task);
        } catch (JedisConnectionException ignored) {
            executeOnFailover.run();
            return performWithoutFailover(description, task, fallback);
        } catch (Exception e) {
            throw Exceptions.handle(Jupiter.LOG, e);
        }
    }

    private <T> T performWithoutFailover(Supplier<String> description, Function<Connection, T> task, RedisDB redis) {
        try (Operation op = new Operation(description, EXPECTED_JUPITER_COMMAND_RUNTIME);
             Jedis jedis = redis.getConnection()) {
            return perform(description, jedis, task);
        } catch (Exception e) {
            throw Exceptions.handle(Jupiter.LOG, e);
        }
    }

    private <T> T perform(Supplier<String> description, Jedis jedis, Function<Connection, T> task) {
        Watch watch = Watch.start();
        try {
            return task.apply(jedis.getClient());
        } catch (JedisConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw Exceptions.handle(Jupiter.LOG, e);
        } finally {
            jupiter.callDuration.addValue(watch.elapsedMillis());
            if (Microtiming.isEnabled()) {
                watch.submitMicroTiming("JUPITER", description.get());
            }
        }
    }

    /**
     * Executes one or more commands and returns a value of the given type without attempting a failover.
     * <p>
     * If the main connection pool isn't available, this will not perform a failover but abort immediatelly.
     * This can be used to execute administrative commands, which have to be executed on the target instance.
     *
     * @param description a description of the actions performed used for debugging and tracing
     * @param task        the actual task to perform using redis
     * @param <T>         the generic type of the result
     * @return a result computed by <tt>task</tt>
     */
    public <T> T queryDirect(Supplier<String> description, Function<Connection, T> task) {
        try (Operation op = new Operation(description, EXPECTED_JUPITER_COMMAND_RUNTIME);
             Jedis jedis = redis.getConnection()) {
            return perform(description, jedis, task);
        } catch (Exception e) {
            throw Exceptions.handle(Jupiter.LOG, e);
        }
    }

    /**
     * Executes one or more Jupiter commands without any return value.
     * <p>
     * If a HA option is present in <tt>jupiter.ha</tt>, the given fallback pool is used in case the main
     * pool isn't available.
     *
     * @param description a description of the actions performed used for debugging and tracing
     * @param task        the actual task to perform using redis
     */
    public void exec(Supplier<String> description, Consumer<Connection> task) {
        query(description, client -> {
            task.accept(client);
            return null;
        });
    }

    /**
     * Executes one or more Jupiter commands without any return value.
     * <p>
     * If the main connection pool isn't available, this will not perform a failover but abort immediately.
     * This can be used to execute administrative commands, which have to be executed on the target instance.
     *
     * @param description a description of the actions performed used for debugging and tracing
     * @param task        the actual task to perform using redis
     */
    public void execDirect(Supplier<String> description, Consumer<Connection> task) {
        queryDirect(description, client -> {
            task.accept(client);
            return null;
        });
    }

    /**
     * Updates the configuration of the instance by using the given YAML string.
     *
     * @param configString the new configuration as YAML string.
     */
    public void updateConfig(String configString) {
        execDirect(() -> "SET_CONFIG", db -> {
            db.sendCommand(CMD_SET_CONFIG, configString);
            db.getStatusCodeReply();
        });
    }

    /**
     * Provides access to the LRU commands of this connection.
     *
     * @param cache the cache to access.
     * @return the wrapper used to access the cache with the given name
     */
    public LRUCache lru(String cache) {
        return new LRUCache(this, cache);
    }

    /**
     * Provides access to the repository management commands of this connection.
     *
     * @return the repository wrapper for this connection
     */
    public Repository repository() {
        return new Repository(this);
    }

    /**
     * Provides access to the InfoGraphDB of this connection.
     *
     * @return the wrapped used to access the IDB of the underlying Jupiter instance.
     */
    public InfoGraphDB idb() {
        return new InfoGraphDB(this);
    }

    protected boolean isFallbackActive() {
        return fallbackActiveUntil > System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return "Jupiter: " + instanceName;
    }
}
