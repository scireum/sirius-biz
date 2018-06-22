/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.sequences;

import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.console.Command;

import javax.annotation.Nonnull;

/**
 * Provides a console command to display all managed sequences.
 *
 * @see Sequences
 */
@Register
public class SequencesCommand implements Command {

    @Part
    private Sequences sequences;

    @Override
    public void execute(Output output, String... params) throws Exception {
        output.apply("%-40s %12s", "NAME", "NEXT VALUE");
        output.separator();
        sequences.getKnownSequences().forEach(sequence -> {
            output.apply("%-40s %12s", sequence, sequences.peekNextValue(sequence));
        });
        output.separator();
    }

    @Override
    public String getDescription() {
        return "Lists all managed sequences.";
    }

    @Nonnull
    @Override
    public String getName() {
        return "sequences";
    }
}
