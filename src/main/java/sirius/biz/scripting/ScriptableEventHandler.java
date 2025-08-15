/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.scripting;

import sirius.kernel.di.std.Part;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the execution of {@link ScriptableEventDispatcher} instances.
 * <p>
 * Classes wanting to profit from scriptable events should create an instance of this class, initialize the
 * available dispatchers via {@link #initializeEventDispatchers()} and then call {@link #handleEvent(ScriptableEvent)}
 * to execute all active dispatchers for a given event. The {@link #isActive()} permits to check if event dispatchers
 * are loaded at all.
 */
public class ScriptableEventHandler {

    @Part
    private static ScriptableEvents scriptableEvents;

    protected List<ScriptableEventDispatcher> eventDispatchers = new ArrayList<>();

    /**
     * Initializes the event dispatchers available for the current tenant.
     */
    public void initializeEventDispatchers() {
        eventDispatchers.addAll(scriptableEvents.fetchDispatcherForCurrentTenant());
    }

    /**
     * Executes all active event dispatchers for the given event.
     *
     * @param event the event to dispatch
     */
    public void handleEvent(ScriptableEvent event) {
        eventDispatchers.stream()
                        .filter(ScriptableEventDispatcher::isActive)
                        .forEach(dispatcher -> dispatcher.handleEvent(event));
    }

    /**
     * Determines if there are any dispatchers available to handle events.
     *
     * @return <tt>true</tt> if at least one dispatcher is active, <tt>false</tt> otherwise
     */
    public boolean isActive() {
        return !eventDispatchers.isEmpty();
    }
}
