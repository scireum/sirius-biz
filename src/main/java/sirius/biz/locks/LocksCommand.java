/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.locks;

import sirius.kernel.commons.Strings;
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
@Register(framework = Locks.FRAMEWORK_LOCKS)
public class LocksCommand implements Command {

    @Part
    private Locks locks;

    @Override
    public void execute(Output output, String... params) throws Exception {
        if (params.length > 0) {
            String name = params[0];

            if (Strings.areEqual(name, "*") || Strings.areEqual(name, "all")) {
                unlockAll(output);
                return;
            }

            unlock(output, name);
        }
        output.line("Use locks <name> to forcefully unlock a lock.");
        output.apply("%-20s %-20s %-20s %-20s", "NAME", "OWNER", "THREAD", "ACQUIRED");
        output.separator();
        locks.getLocks().forEach(lock -> {
            output.apply("%-20s %-20s %-20s %-20s",
                         lock.getName(),
                         lock.getOwner(),
                         lock.getThread(),
                         NLS.toUserString(lock.getAcquired()));
        });
        output.separator();
    }

    private void unlock(Output output, String name) {
        output.apply("Unlocking: %s", name);
        locks.unlock(name, true);
    }

    private void unlockAll(Output output) {
        locks.getLocks().forEach(lock -> {
            unlock(output, lock.getName());
        });
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
