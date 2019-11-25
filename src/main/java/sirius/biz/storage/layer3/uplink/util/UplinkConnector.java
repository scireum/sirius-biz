/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink.util;

import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.async.Operation;
import sirius.kernel.health.Exceptions;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Encapsulates a pooled connector.
 * <p>
 * This is mainly used for book keeping by the {@link UplinkConnectorPool}.
 *
 * @param <C> the effective connector being wrapped
 */
public class UplinkConnector<C> implements Closeable {

    protected C connector;
    protected Consumer<UplinkConnector<C>> closeCallback;
    protected Operation operation;

    protected UplinkConnector(C connector) {
        this.connector = connector;
    }

    /**
     * Returns the wrapped connector.
     *
     * @return the wrapped connector
     */
    public C connector() {
        return connector;
    }

    @Override
    public void close() throws IOException {
        safeClose();
    }

    /**
     * Closes the connector and handles any exception internally.
     */
    public void safeClose() {
        try {
            closeCallback.accept(this);
        } catch (Exception e) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(e)
                      .withSystemErrorMessage("Layer 3/Uplinks: An error occurred while closing a connector: %s (%s)")
                      .handle();
        }
    }
}
