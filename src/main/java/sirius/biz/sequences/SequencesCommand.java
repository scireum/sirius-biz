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
@Register(framework = Sequences.FRAMEWORK_SEQUENCES)
public class SequencesCommand implements Command {

    @Part
    private Sequences sequences;

    @Override
    public void execute(Output output, String... params) throws Exception {
        if (params.length == 2 || params.length == 3) {
            specifyNextSequence(params[0], isForced(params), Long.parseLong(params[1]), output);
        } else {
            outputUsage(output);
        }
        listSequences(output);
    }

    private boolean isForced(String... params) {
        return params.length == 3 && "force".equalsIgnoreCase(params[2]);
    }

    private void specifyNextSequence(String sequenceName, boolean force, long nextValue, Output output) {
        sequences.setNextValue(sequenceName, nextValue, force);
        output.apply("Sequence %s set to %d.", sequenceName, nextValue);
    }

    private void outputUsage(Output output) {
        output.apply("Usage:");
        output.apply("'sequences' to list all known sequences");
        output.apply("'sequences NAME NEXTVALUE' to set the next value for a sequence");
        output.apply(
                "'sequences NAME NEXTVALUE force' to force the next value for a sequence !DANGEROUS! as it can lead to duplicate ids");
    }

    private void listSequences(Output output) {
        output.separator();
        output.apply("%-40s %12s", "NAME", "NEXT VALUE");
        output.separator();
        sequences.getKnownSequences().forEach(sequence -> {
            output.apply("%-40s %12s", sequence, sequences.peekNextValue(sequence));
        });
        output.separator();
    }

    @Override
    public String getDescription() {
        return "Lists or modifies managed sequences.";
    }

    @Nonnull
    @Override
    public String getName() {
        return "sequences";
    }
}
