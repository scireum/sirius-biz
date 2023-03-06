/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink.util;

import org.apache.commons.pool2.impl.DefaultPooledObjectInfo;
import org.apache.commons.pool2.impl.GenericObjectPool;
import sirius.kernel.commons.Values;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.console.Command;
import sirius.kernel.nls.NLS;

import javax.annotation.Nonnull;
import java.time.Instant;

/**
 * Provides an admin command to monitor and drain all pools managed by {@link UplinkConnectorPool}.
 */
@Register
public class ListUplinkConnectorsCommand implements Command {

    @Part
    private UplinkConnectorPool connectorPool;

    @Override
    public String getDescription() {
        return "Lists all connector pools of the Virtual File Systems.";
    }

    @Nonnull
    @Override
    public String getName() {
        return "vfs-uplink-pools";
    }

    @Override
    public void execute(Output output, String... params) throws Exception {
        boolean drainPools = "clear".equals(Values.of(params).at(0).asString());
        if (drainPools) {
            output.line("Draining all pools...");
        } else {
            output.line("Use vfs-uplink-pools clear to drain all pools.");
        }
        output.blankLine();

        connectorPool.fetchPools().forEach((config, pool) -> {
            if (drainPools) {
                drainPool(output, config, pool);
            }
            outputPool(output, config, pool);
        });
    }

    protected void drainPool(Output output,
                             UplinkConnectorConfig<?> config,
                             GenericObjectPool<UplinkConnector<?>> pool) {

        try {
            output.apply("Draining pool: %s (%s)", config.host, config.user);
            pool.clear();
        } catch (Exception e) {
            output.apply("Draining of %s (%s) failed: %s", config.host, config.user, Exceptions.handle(e).getMessage());
        }
    }

    private void outputPool(Output output,
                            UplinkConnectorConfig<?> config,
                            GenericObjectPool<UplinkConnector<?>> pool) {
        output.apply("%s (%s)", config.host, config.user);
        output.apply("I/A %-20s %-20s %-20s %12s", "CREATED", "BORROWED", "RETURNED", "BORROW-COUNT");
        output.separator();
        for (DefaultPooledObjectInfo info : pool.listAllObjects()) {
            // When the object info is not returned on the first borrow the timings are the same, so we have to do some sanity checks here.
            boolean active = info.getLastReturnTime() < info.getLastBorrowTime()
                             && info.getLastReturnTime() > info.getCreateTime();
            output.apply("%-3s %-20s %-20s %-20s %12s",
                         active ? "A" : "I",
                         NLS.toUserString(Instant.ofEpochMilli(info.getCreateTime())),
                         NLS.toUserString(Instant.ofEpochMilli(info.getLastBorrowTime())),
                         NLS.toUserString(Instant.ofEpochMilli(info.getLastReturnTime())),
                         info.getBorrowedCount());
            if (active) {
                output.line(info.getPooledObjectToString());
                output.blankLine();
            }
        }

        output.blankLine();
    }
}
