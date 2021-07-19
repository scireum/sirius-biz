/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink.util;

import sirius.kernel.commons.Strings;
import sirius.kernel.settings.Extension;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

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
    protected UplinkConnectorConfig(Extension config) {
        this.label = config.getId();
        this.host = config.get("host").asString();
        this.port = config.get("port").asInt(getDefaultPort());
        this.user = config.get("user").asString();
        this.password = config.get("password").asString();
        this.maxIdle = config.get("maxIdle").asInt(DEFAULT_MAX_IDLE);
        this.maxActive = config.get("maxActive").asInt(DEFAULT_MAX_ACTIVE);
        this.connectTimeoutMillis = config.get("connectTimeoutMillis").asInt(DEFAULT_CONNECT_TIMEOUT_MILLIS);
        this.readTimeoutMillis = config.get("readTimeoutMillis").asInt(DEFAULT_READ_TIMEOUT_MILLIS);
        this.idleTimeoutMillis = config.get("idleTimeoutMillis").asInt(DEFAULT_IDLE_TIMEOUT_MILLIS);
        this.maxWaitMillis = config.get("maxWaitMillis").asInt(DEFAULT_MAX_WAIT_MILLIS);
    }

    /**
     * Determines the default port to use.
     *
     * @return the default port to use in case the user didn't specify any
     */
    protected abstract int getDefaultPort();

    /**
     * Creates and initiaizes a new connector using this config.
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
    public int hashCode() {
        return Objects.hash(host, port, user, password);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (!(obj instanceof UplinkConnectorConfig<?> other)) {
            return false;
        }

        return Objects.equals(host, other.host)
               && Objects.equals(port, other.port)
               && Objects.equals(user, other.user)
               && Objects.equals(password, other.password);
    }

    @Override
    public String toString() {
        return Strings.apply("%s (%s@%s)", label, user, host);
    }
}
