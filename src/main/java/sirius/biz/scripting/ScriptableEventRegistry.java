/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.scripting;

import sirius.kernel.commons.Callback;
import sirius.pasta.noodle.sandbox.NoodleSandbox;

/**
 * Provides the interface as seen by the scripting engine to register custom event handlers.
 * <p>
 * The tenant specific script is provided with an instance of this registry and can then add handlers
 * as needed.
 */
public interface ScriptableEventRegistry {

    /**
     * Adds a handler for the given event type.
     *
     * @param eventType the type of events to handle
     * @param handler   the handler to handle the event
     * @param <E>       the generic type of the event
     */
    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    <E extends ScriptableEvent> void registerHandler(Class<E> eventType, Callback<E> handler);

    /**
     * Adds a typed handler for the given event type and inner type
     *
     * @param eventType the type of events to handle
     * @param type      the inner type within the event to process
     * @param handler   the handler to handle the event
     * @param <T>       the generic inner type of the event
     * @param <E>       the generic type of the event
     */
    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    <T, E extends TypedScriptableEvent<T>> void registerTypedHandler(Class<E> eventType,
                                                                     Class<T> type,
                                                                     Callback<E> handler);
}
