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
import java.util.Locale;

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
        if (params.length == 0) {
            listSequences(output);
            return;
        }
        if (params.length == 3) {
            setSequence(output, params);
            return;
        }
        outputUsage(output);
    }

    private void outputUsage(Output output) {
        output.apply("Usage:");
        output.apply("'sequences' to list all known sequences");
        output.apply("'sequences set NAME NEXTVALUE' to set the next value for a sequence");
        output.apply("'sequences force NAME NEXTVALUE' to force the next value for a sequence !DANGEROUS! as it can lead to duplicate ids");
    }

    private void setSequence(Output output, String... params) {
        boolean force;
        String forceParam = params[0].toLowerCase(Locale.ROOT);
        if ("force".equals(forceParam)) {
            force = true;
        } else if ("set".equals(forceParam)) {
            force = false;
        } else {
            outputUsage(output);
            return;
        }
        String sequence = params[1];
        long nextValue = Long.parseLong(params[2]);
        sequences.setNextValue(sequence, nextValue, force);
        output.apply("Sequence %s set to %d.", sequence, nextValue);
    }

    private void listSequences(Output output) {
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
