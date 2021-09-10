/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.flags;

import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.console.Command;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Period;

/**
 * Permits to kill an execution flag manually.
 */
@Register
public class KillExecutionFlagCommand implements Command {

    @Part
    @Nullable
    private ExecutionFlags executionFlags;

    @Override
    public void execute(Output output, String... args) throws Exception {
        if (executionFlags == null) {
            output.line("No ExecutionFlags provide enabled...");
            return;
        }

        if (args.length != 2) {
            output.line("Usage: kill-execution-flag <reference> <flag>");
            return;
        }

        executionFlags.storeExecutionFlag(args[0], args[1], null, Period.ZERO);
        output.line("The flag has been removed successfully.");
    }

    @Override
    public String getDescription() {
        return "Kills the execution flag with the given name";
    }

    @Nonnull
    @Override
    public String getName() {
        return "kill-execution-flag";
    }
}
