/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.i5;

import com.google.common.collect.Maps;
import sirius.kernel.Lifecycle;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Average;
import sirius.kernel.health.Counter;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.settings.Extension;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Provides access to pooled i5 connections.
 * <p>
 * In contrast to native pooling provided by jt400, this facility permits to configure connections using the system
 * config and additionally to share these accross multiple pools.
 */
@Register(classes = {I5Connector.class, Lifecycle.class})
public class I5Connector implements Lifecycle {

    public static final Log LOG = Log.get("i5");
    protected Counter borrows = new Counter();
    protected Counter calls = new Counter();
    protected Average callDuration = new Average();
    protected Average callUtilization = new Average();
    protected Map<Tuple<String, String>, I5ConnectionPool> pools = Maps.newConcurrentMap();

    /**
     * Provides a connection from the connection pool created for the configured i5.
     * <p>
     * For multi tenant scenarios several pools to the same physical host can be created, by supplying different
     * tenantNames.
     *
     * @param configName the name of the config entry (<tt>i5.[configEntry]</tt>) which is used to fetch the
     *                   pool configuration from the system config.
     * @param tenantName the optional tenant name to create multiple pools for one host
     * @param setup      an optional setup procedure which is invoked once for each new connection. Note that this
     *                   parameter is only considered for the first call which creates the connection pool.
     * @return either a new or a pooled connection from the specified connection pool
     */
    public I5Connection getConnection(String configName,
                                      @Nullable String tenantName,
                                      @Nullable Consumer<I5Connection> setup) {
        Tuple<String, String> key = Tuple.create(configName, tenantName);
        I5ConnectionPool pool = pools.computeIfAbsent(key, k -> {
            Extension ext = Sirius.getSettings().getExtension("i5", k.getFirst());
            if (ext == null || ext.isDefault()) {
                throw Exceptions.handle()
                                .to(LOG)
                                .withSystemErrorMessage(
                                        "Cannot obtain a connection to '%s'. No suitable configuration (i5.%s) found!",
                                        configName,
                                        configName)
                                .handle();
            }

            return new I5ConnectionPool(tenantName + "/" + configName, this, ext, setup);
        });

        return pool.getConnection();
    }

    @Override
    public void started() {
    }

    @Override
    public void stopped() {
        pools.entrySet().stream().map(Map.Entry::getValue).forEach(I5ConnectionPool::release);
    }

    @Override
    public void awaitTermination() {
    }

    @Override
    public String getName() {
        return "i5";
    }
}
