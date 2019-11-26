/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink.util;

import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.console.Command;

import javax.annotation.Nonnull;

/**
 * Permits to output all active uplinks and the pool utilization.
 */
@Register
public class UplinksCommand implements Command {

    @Part
    private UplinkConnectorPool uplinkConnectorPool;

    @Override
    public void execute(Output output, String... params) throws Exception {
        output.apply("%-20s %-21s %-20s %6s %4s %4s", "UPLINK", "HOST", "USER", "ACTIVE", "IDLE", "USED");
        output.separator();
        uplinkConnectorPool.fetchPools().forEach((config, pool) -> {
            output.apply("%-20s %-21s %-20s %6s %4s %4s",
                         config.label,
                         config.host,
                         config.user,
                         pool.getNumActive(),
                         pool.getNumIdle(),
                         pool.getBorrowedCount());
        });
        output.separator();
        output.blankLine();
    }

    @Override
    public String getDescription() {
        return "Reports all active uplinks of the virtual file system.";
    }

    @Nonnull
    @Override
    public String getName() {
        return "uplinks";
    }
}
