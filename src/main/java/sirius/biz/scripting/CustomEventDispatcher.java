/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.scripting;

/**
 * Describes a dispatcher which can handle custom events.
 * <p>
 * This is usually fetched via {@link CustomEvents} and will handle all events for a given tenant,
 * based on a given script. If no custom handling script is present, a <tt>NOOP</tt> dispatcher is used
 * which will be marked as {@link #isActive() inactive} (so that some events might get optimized away).
 */
public interface CustomEventDispatcher {

    /**
     * Determines if this dispatcher is active and will actually handle events.
     *
     * @return <tt>true</tt> if a real dispatcher is present, <tt>false</tt> if a <tt>NOOP</tt> dispatcher is used
     */
    boolean isActive();

    /**
     * Handles the given event.
     *
     * @param event the event to handle
     */
    void handleEvent(CustomEvent event);
}
