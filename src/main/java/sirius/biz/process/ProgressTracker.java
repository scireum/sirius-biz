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
 * Allows to track the progress of a long-running operation and calculates the estimated completion time.
 */
public class ProgressTracker {
    private final long total;
    private final LocalDateTime start;
    private final ProcessContext processContext;
    private long current = 0;

    private boolean finished = false;

    private String message = null;

    /**
     * Initializes the progress tracker with a total amount of units.
     *
     * @param processContext the process context to update the state message
     * @param total          the total amount of units to track
     */
    public ProgressTracker(ProcessContext processContext, long total) {
        if (total < 0) {
            throw new IllegalArgumentException("Total must be >= 0");
        }
        this.start = LocalDateTime.now();
        this.total = total;
        this.processContext = processContext;
    }

    /**
     * Increments the processed unit by one and recalculates the progress.
     */
    public void increment() {
        increment(1);
    }

    /**
     * Increments the processed unit by the amount given and recalculates the progress.
     *
     * @param amount the amount to increment the processed units by
     */
    public void increment(long amount) {
        assertTrackingStarted();
        if (finished) {
            throw new IllegalStateException("Cannot increment a finished progress tracker");
        }

        current = current + amount;
        updateIntermediateState();
    }

    /**
     * Finishes the progress tracking and updates the process with a final message containing the total elapsed time.
     */
    public void finish() {
        assertTrackingStarted();
        finished = true;
        updateFinalState();
    }

    /**
     * Updates the plain-text additional message
     *
     * @param message the message to display
     */
    public void updateMessage(@Nullable String message) {
        assertTrackingStarted();
        this.message = message;
        updateState();
    }

    private void updateState() {
        if (finished) {
            updateFinalState();
        } else {
            updateIntermediateState();
        }
    }

    private void updateIntermediateState() {
        processContext.tryUpdateState(NLS.fmtr("ProgressTracker.state")
                                         .set("progress", computeProgress())
                                         .set("remaining",
                                              computeRemainingTime().map(remaining -> NLS.convertDuration(remaining,
                                                                                                          true,
                                                                                                          false))
                                                                    .orElse(null))
                                         .set("completion", computeCompletionTime().map(NLS::toUserString).orElse(null))
                                         .set("message", message)
                                         .smartFormat());
    }

    private void updateFinalState() {
        processContext.forceUpdateState(NLS.fmtr("ProgressTracker.total")
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

    private void assertTrackingStarted() {
        if (processContext == null) {
            throw new IllegalStateException("The progress tracker has not been started yet!");
        }
    }
}
