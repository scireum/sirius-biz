/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.locks;

import sirius.db.mixing.OMA;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.console.Command;
import sirius.kernel.nls.NLS;

import javax.annotation.Nonnull;

/**
 * Provides a console command to display all locks.
 *
 * @see Locks
 */
@Register
public class LocksCommand implements Command {

    @Part
    private OMA oma;

    @Override
    public void execute(Output output, String... params) throws Exception {
        output.apply("%-20s %-20s %-20s %-20s", "NAME", "OWNER", "THREAD", "ACQUIRED");
        output.separator();
        oma.select(ManagedLock.class).orderAsc(ManagedLock.ACQUIRED).iterateAll(lock -> {
            output.apply("%-20s %-20s %-20s %-20s",
                         lock.getName(),
                         lock.getOwner(),
                         lock.getThread(),
                         NLS.toUserString(lock.getAcquired()));
        });
        output.separator();
    }

    @Override
    public String getDescription() {
        return "Lists all distributed named locks.";
    }

    @Nonnull
    @Override
    public String getName() {
        return "locks";
    }
}
