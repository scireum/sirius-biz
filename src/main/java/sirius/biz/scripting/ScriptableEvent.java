/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.scripting;

import sirius.kernel.health.HandledException;

import java.util.Optional;

/**
 * Provides a base class for all custom events handled by a {@link ScriptableEventDispatcher}.
 */
public abstract class ScriptableEvent {

    /**
     * Stores if all event handlers completed successfully.
     */
    protected boolean success;

    /**
     * Stores if an error / exception occurred while invoking an event handler.
     */
    protected boolean failed;

    /**
     * Stores the exception which occurred while invoking an event handler.
     */
    protected HandledException error;

    /**
     * Determines if the event was successful.
     *
     * @return <tt>true</tt> if the event was successful, <tt>false</tt> otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Determines if the event failed (an exception occurred within an event handler).
     * <p>
     * Note, that the exception itself can be obtained via {@link #getError()}.
     *
     * @return <tt>true</tt> if the event failed, <tt>false</tt> otherwise
     */
    public boolean isFailed() {
        return failed;
    }

    /**
     * Provides the error which occurred when handling the event.
     *
     * @return the error that occurred wrapped as optional or an empty optional if the event was successful
     */
    public Optional<HandledException> getError() {
        return Optional.ofNullable(error);
    }
}
