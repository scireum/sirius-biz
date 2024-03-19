/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.util;

import sirius.kernel.health.Exceptions;

import java.io.IOException;

/**
 * Helps to distinguish an initial first attempt from a retry.
 * <p>
 * It can be used by any kind of io operation which are essentially expected to fail sometime, but which might
 * benefit from attempting a retry. Therefore, the helper method {@link #shouldThrow(Exception)}
 * can be used to determine if an <tt>IOException</tt> is swallowed if it occurs during a first attempt but
 * will be thrown during a retry.
 * <p>
 * The pattern to use this looks something like:
 * <pre>{@code
 * for(Attempt attempt : Attempt.values()) {
 *     try {
 *         ...do something
 *         return;
 *     } catch(Exception exception) {
 *         if (attempt.shouldThrow(exception)) {
 *             throw Exceptions.handle...
 *         }
 *     }
 * }
 * }</pre>
 * <p>
 * This approach permits to perform inner returns and also enables some optimizations to the compiler (as opposed to
 * using lambdas).
 */
public enum Attempt {
    FIRST_ATTEMPT, RETRY;

    /**
     * Determines if the given exception should be thrown.
     *
     * @param exception the exception to check
     * @return <tt>true</tt> if the exception should be thrown, <tt>false</tt> if it should be swallowed
     */
    public boolean shouldThrow(Exception exception) {
        boolean decision = this == RETRY || !(Exceptions.getRootCause(exception) instanceof IOException);

        if (!decision) {
            StorageUtils.LOG.FINE("An exception was suppressed in favor of a retry: %s (%s)",
                                  exception.getMessage(),
                                  exception.getClass().getName());
        }

        return decision;
    }
}
