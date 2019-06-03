/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.isenguard;

/**
 * Represents a marker interface for entities on which rate-limits are applied.
 * <p>
 * The {@link RateLimitEventsReportJobFactory} will recognize such entities
 * and return itself as "matching job".
 */
public interface RateLimitedEntity {

    /**
     * Defines the effective value which is used as "scope".
     *
     * @return the value to be used as <tt>scope</tt>
     */
    String getRateLimitScope();
}
