/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink.util;

import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Provides a configuration which tells the {@link UplinkConnectorPool} how to create and maintain a
 * {@link UplinkConnector}.
 *
 * @param <C> the type of connectors being configured
 */
public abstract class UplinkConnectorConfig<C> {

    private static final int DEFAULT_MAX_IDLE = 1;
    private static final int DEFAULT_MAX_ACTIVE = 5;
    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = (int) TimeUnit.SECONDS.toMillis(10);
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = (int) TimeUnit.SECONDS.toMillis(10);
    private static final int DEFAULT_IDLE_TIMEOUT_MILLIS = (int) TimeUnit.MINUTES.toMillis(10);
    private static final int DEFAULT_MAX_WAIT_MILLIS = (int) TimeUnit.SECONDS.toMillis(10);

    /**
     * Determines the host to connect to.
     */
    public static final String CONFIG_HOST = "host";

    /**
     * Determines the host to connect to.
     */
    public static final String CONFIG_PORT = "port";

    /**
     * Determines the username used for authentication.
     */
    public static final String CONFIG_USER = "user";

    /**
     * Determines the password used for authentication.
     */
    public static final String CONFIG_PASSWORD = "password";

    /**
     * Specifies the maximal number of idle clients to keep in the connector pool.
     */
    public static final String CONFIG_MAX_IDLE = "maxIdle";

    /**
     * Specifies the maximal number of concurrently active connections to create.
     */
    public static final String CONFIG_MAX_ACTIVE = "maxActive";

    /**
     * Specifies the connect timeout.
     */
    public static final String CONFIG_CONNECT_TIMEOUT_MILLIS = "connectTimeoutMillis";

    /**
     * Specifies the read timeout.
     */
    public static final String CONFIG_READ_TIMEOUT_MILLIS = "readTimeoutMillis";

    /**
     * Specifies the amount of time after which an idle client is removed from the pool.
     */
    public static final String CONFIG_IDLE_TIMEOUT_MILLIS = "idleTimeoutMillis";

    /**
     * Specifies the max time to wait for a client to become available before creating a new one.
     */
    public static final String CONFIG_MAX_WAIT_MILLIS = "maxWaitMillis";

    protected String label;
    protected String host;
    protected int port;
    protected String user;
    protected String password;

    protected int maxIdle;
    protected int maxActive;
    protected int connectTimeoutMillis;
    protected int readTimeoutMillis;
    protected int idleTimeoutMillis;
    protected int maxWaitMillis;

    /**
     * Loads the whole configuration from the given config block.
     *
     * @param config the config to read all settings from
     */
    protected UplinkConnectorConfig(String id, Function<String, Value> config) {
        this.label = id;
        this.host = config.apply(CONFIG_HOST).asString();
        this.port = config.apply(CONFIG_PORT).asInt(getDefaultPort());
        this.user = config.apply(CONFIG_USER).asString();
        this.password = config.apply(CONFIG_PASSWORD).asString();
        this.maxIdle = config.apply(CONFIG_MAX_IDLE).asInt(DEFAULT_MAX_IDLE);
        this.maxActive = config.apply(CONFIG_MAX_ACTIVE).asInt(DEFAULT_MAX_ACTIVE);
        this.connectTimeoutMillis = config.apply(CONFIG_CONNECT_TIMEOUT_MILLIS).asInt(DEFAULT_CONNECT_TIMEOUT_MILLIS);
        this.readTimeoutMillis = config.apply(CONFIG_READ_TIMEOUT_MILLIS).asInt(DEFAULT_READ_TIMEOUT_MILLIS);
        this.idleTimeoutMillis = config.apply(CONFIG_IDLE_TIMEOUT_MILLIS).asInt(DEFAULT_IDLE_TIMEOUT_MILLIS);
        this.maxWaitMillis = config.apply(CONFIG_MAX_WAIT_MILLIS).asInt(DEFAULT_MAX_WAIT_MILLIS);
    }

    /**
     * Determines the default port to use.
     *
     * @return the default port to use in case the user didn't specify any
     */
    protected abstract int getDefaultPort();

    /**
     * Creates and initializes a new connector using this config.
     *
     * @return the newly created connector
     */
    protected abstract C create();

    /**
     * Validates the given connector
     *
     * @param connector the connector to validate
     * @return <tt>true</tt> if the connector can be used, <tt>false</tt> if it should be closed and a new one should
     * be created.
     */
    protected abstract boolean validate(C connector);

    /**
     * Closes the given connector.
     *
     * @param connector the connector to close
     */
    protected abstract void safeClose(C connector);

    @Override
    public String toString() {
        return Strings.apply("%s (%s@%s)", label, user, host);
    }
}
