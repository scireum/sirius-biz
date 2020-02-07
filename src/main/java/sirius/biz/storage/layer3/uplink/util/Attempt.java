/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink.util;

import sirius.biz.storage.layer3.uplink.sftp.SFTPUplink;
import sirius.biz.storage.util.StorageUtils;

import java.io.IOException;

/**
 * Helps to distinguishes an initial first attempt from a rety.
 * <p>
 * If is used by uplinks like {@link sirius.biz.storage.layer3.uplink.ftp.FTPUplink} or {@link SFTPUplink} to
 * permit a retry for all IO related operations. Therefore the helper method {@link #shouldThrow(Exception)}
 * can be used to determine if an <tt>IOException</tt> is swallowed if it occurs during a first attempt but
 * will be thrown during a retry.
 * <p>
 * The pattern to use this looks something like:
 * <pre>{@code
 * for(Attempt attempt : Attempt.values()) {
 *     try {
 *         ...do something
 *         return;
 *     } catch(Exception e) {
 *         if (attempt.shouldThrow(e)) {
 *             throw Exceptions.handle...
 *         }
 *     }
 * }
 * }</pre>
 * <p>
 * This approach permits to perform inner returns and also enables some optimizations to the compiler (as opposed to
 * use lambdas).
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
        boolean decision =
                this == RETRY || !((exception instanceof IOException) || (exception.getCause() instanceof IOException));

        if (!decision) {
            StorageUtils.LOG.FINE("An exception was suppressed in favor of a retry: %s (%s)",
                                  exception.getMessage(),
                                  exception.getClass().getName());
        }

        return decision;
    }
}
