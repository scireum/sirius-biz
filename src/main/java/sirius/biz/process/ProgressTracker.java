/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Allows to track the progress of a long running operation and calculates the estimated completion time.
 */
public class ProgressTracker {
    private final long total;
    private final LocalDateTime start;
    private long current = 0;

    private boolean finished = false;

    private String message = null;

    /**
     * Initializes the progress tracker with a total amount of units.
     *
     * @param total the total amount of units to track
     * @return the progress tracker
     */
    public static ProgressTracker start(long total) {
        return new ProgressTracker(total);
    }

    private ProgressTracker(long total) {
        if (total < 0) {
            throw new IllegalArgumentException("Total must be >= 0");
        }
        this.start = LocalDateTime.now();
        this.total = total;
    }

    /**
     * Increments the processed unit by one and recalculates the progress.
     *
     * @param process the process context to update the state message
     */
    public void increment(ProcessContext process) {
        increment(process, 1);
    }

    /**
     * Increments the processed unit by the amount given and recalculates the progress.
     *
     * @param amount  the amount to increment the processed units by
     * @param process the process context to update the state message
     */
    public void increment(ProcessContext process, long amount) {
        if (finished) {
            throw new IllegalStateException("Cannot increment a finished progress tracker");
        }

        current = current + amount;
        updateIntermediateState(process);
    }

    /**
     * Finishes the progress tracking and updates the process with a final message containing the total elapsed time.
     *
     * @param process the process context to update the state message
     */
    public void finish(ProcessContext process) {
        finished = true;
        updateFinalState(process);
    }

    /**
     * Updates the plain-text additional message
     *
     * @param process the process context to update the state message
     * @param message the message to display
     */
    public void updateMessage(ProcessContext process, @Nullable String message) {
        this.message = message;
        updateState(process);
    }

    private void updateState(ProcessContext process) {
        if (finished) {
            updateFinalState(process);
        } else {
            updateIntermediateState(process);
        }
    }

    private void updateIntermediateState(ProcessContext process) {
        process.tryUpdateState(NLS.fmtr("ProgressTracker.state")
                                  .set("progress", computeProgress())
                                  .set("remaining",
                                       computeRemainingTime().map(remaining -> NLS.convertDuration(remaining,
                                                                                                   true,
                                                                                                   false)).orElse(null))
                                  .set("completion", computeCompletionTime().map(NLS::toUserString).orElse(null))
                                  .set("message", message)
                                  .smartFormat());
    }

    private void updateFinalState(ProcessContext process) {
        process.forceUpdateState(NLS.fmtr("ProgressTracker.total")
                                    .set("total", NLS.convertDuration(computeElapsedDuration(), true, false))
                                    .set("message", message)
                                    .smartFormat());
    }

    private int computeProgress() {
        if (total == 0) {
            return 0;
        }
        return (int) ((double) current / total * 100);
    }

    private Optional<LocalDateTime> computeCompletionTime() {
        if (total == 0 || current == 0) {
            return Optional.empty();
        }
        long elapsedMillis = computeElapsedDuration().toMillis();
        long totalMillis = (long) ((double) elapsedMillis * total / current);
        return Optional.of(start.plus(totalMillis, ChronoUnit.MILLIS));
    }

    private Optional<Duration> computeRemainingTime() {
        if (total == 0 || current == 0) {
            return Optional.empty();
        }
        long elapsedMillis = computeElapsedDuration().toMillis();
        long totalMillis = (long) ((double) elapsedMillis * total / current);
        return Optional.of(Duration.ofMillis(totalMillis - elapsedMillis));
    }

    private Duration computeElapsedDuration() {
        return Duration.between(start, LocalDateTime.now());
    }
}
