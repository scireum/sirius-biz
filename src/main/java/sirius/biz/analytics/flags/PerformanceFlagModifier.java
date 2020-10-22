/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.flags;

import javax.annotation.CheckReturnValue;

/**
 * Permits to efficiently modify the performance flags of an entity.
 */
public interface PerformanceFlagModifier {

    /**
     * Sets the given flag to the given value.
     * <p>
     * Note that {@link #commit()} has to be invoked in order to actually change the flag.
     *
     * @param flag        the flag to set
     * @param targetValue the value to set
     * @return the modifier itself for fluent method calls
     */
    @CheckReturnValue
    PerformanceFlagModifier set(PerformanceFlag flag, boolean targetValue);

    /**
     * Sets the given flag to the given <tt>true</tt>.
     * <p>
     * Note that {@link #commit()} has to be invoked in order to actually change the flag.
     *
     * @param flag the flag to set
     * @return the modifier itself for fluent method calls
     */
    @CheckReturnValue
    PerformanceFlagModifier set(PerformanceFlag flag);

    /**
     * Sets the given flag to the given <tt>false</tt>.
     * <p>
     * Note that {@link #commit()} has to be invoked in order to actually change the flag.
     *
     * @param flag the flag to clear
     * @return the modifier itself for fluent method calls
     */
    @CheckReturnValue
    PerformanceFlagModifier clear(PerformanceFlag flag);

    /**
     * Persists all changes which have been made using this modifier.
     */
    void commit();
}
