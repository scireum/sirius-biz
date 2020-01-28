/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink.util;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.async.ExecutionPoint;
import sirius.kernel.async.Operation;

import java.time.Duration;

/**
 * Provides a bridge between {@link UplinkConnectorConfig uplink configs} and the {@link GenericObjectPool object pools}.
 */
class UplinkConnectorFactory implements PooledObjectFactory<UplinkConnector<?>> {

    private UplinkConnectorConfig<?> config;
    private GenericObjectPool<UplinkConnector<?>> pool;

    UplinkConnectorFactory(UplinkConnectorConfig<?> uplinkConnectorConfig) {
        config = uplinkConnectorConfig;
    }

    protected void linkToPool(GenericObjectPool<UplinkConnector<?>> pool) {
        this.pool = pool;
    }

    @Override
    public PooledObject<UplinkConnector<?>> makeObject() throws Exception {
        UplinkConnector<?> connector = new UplinkConnector<>(config.create());
        if (StorageUtils.LOG.isFINE()) {
            StorageUtils.LOG.FINE("Layer 3/Uplinks: Creating an object for %s: %s", config, connector.toSimpleString());
        }
        return new DefaultPooledObject<>(connector);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean validateObject(PooledObject<UplinkConnector<?>> pooledObject) {
        boolean validationResult =
                ((UplinkConnectorConfig<Object>) config).validate(pooledObject.getObject().connector());
        if (StorageUtils.LOG.isFINE()) {
            StorageUtils.LOG.FINE("Layer 3/Uplinks: Validating an object for %s: %s - Valid: %s",
                                  config,
                                  pooledObject.getObject().toSimpleString(),
                                  validationResult);
        }

        return validationResult;
    }

    @Override
    public void activateObject(PooledObject<UplinkConnector<?>> pooledObject) throws Exception {
        pooledObject.getObject().operation = new Operation(config::toString, Duration.ofHours(6));
        pooledObject.getObject().closeCallback = pool::returnObject;
        pooledObject.getObject().borrowedPoint = ExecutionPoint.fastSnapshot();

        if (StorageUtils.LOG.isFINE()) {
            StorageUtils.LOG.FINE("Layer 3/Uplinks: Activating an object for %s: %s",
                                  config,
                                  pooledObject.getObject().toSimpleString());
        }
    }

    @Override
    public void passivateObject(PooledObject<UplinkConnector<?>> pooledObject) throws Exception {
        if (StorageUtils.LOG.isFINE()) {
            StorageUtils.LOG.FINE("Layer 3/Uplinks: Passivating an object for %s: %s",
                                  config,
                                  pooledObject.getObject().toSimpleString());
        }

        pooledObject.getObject().operation.close();
        pooledObject.getObject().closeCallback = null;
        pooledObject.getObject().borrowedPoint = null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void destroyObject(PooledObject<UplinkConnector<?>> pooledObject) throws Exception {
        if (StorageUtils.LOG.isFINE()) {
            StorageUtils.LOG.FINE("Layer 3/Uplinks: Destroying an object for %s: %s",
                                  config,
                                  pooledObject.getObject().toSimpleString());
        }

        ((UplinkConnectorConfig<Object>) config).safeClose(pooledObject.getObject().connector());
    }
}
