/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.scripting;

import sirius.biz.process.Processes;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Callback;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.nls.NLS;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides a {@link CustomEventDispatcher} which also implements {@link CustomEventRegistry}.
 * <p>
 * An instance of this class can be first supplied to a custom script in order to pick up handlers and will then
 * be used to dispatch upcoming events to these handlers.
 */
public class ScriptBasedCustomEventDispatcher implements CustomEventDispatcher, CustomEventRegistry {

    @Part
    private static Processes processes;

    private volatile boolean active = false;

    private final Map<String, Callback<? extends CustomEvent>> handlers = new HashMap<>();

    @Override
    public <E extends CustomEvent> void registerHandler(Class<E> eventType, Callback<E> handler) {
        handlers.put(eventType.getName(), handler);
        active = true;
    }

    @Override
    public <T, E extends TypedCustomEvent<T>> void registerTypedHandler(Class<E> eventType,
                                                                        Class<T> type,
                                                                        Callback<E> handler) {
        handlers.put(buildTypedEventHandlerName(eventType, type), handler);
        active = true;
    }

    private String buildTypedEventHandlerName(Class<?> eventType, Class<?> type) {
        return eventType.getName() + "::" + type.getName();
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handleEvent(CustomEvent event) {
        Callback<CustomEvent> handler = (Callback<CustomEvent>) handlers.get(determineEventKey(event));
        if (handler == null) {
            return;
        }

        Watch watch = Watch.start();
        try {
            handler.invoke(event);
            event.success = true;
        } catch (HandledException handledException) {
            handleEventHandlerException(handledException);
            event.failed = true;
            event.error = handledException;
        } catch (Exception e) {
            HandledException handledException = Exceptions.handle(Scripting.LOG, e);
            handleEventHandlerException(handledException);
            event.failed = true;
            event.error = handledException;
        }
        TaskContext.get().addTiming(NLS.get("CustomEventHandler.customEvents"), watch.elapsedMillis());
    }

    private String determineEventKey(CustomEvent event) {
        if (event instanceof TypedCustomEvent<?> typedEvent) {
            return buildTypedEventHandlerName(typedEvent.getClass(), typedEvent.getType());
        } else {
            return event.getClass().getName();
        }
    }

    private void handleEventHandlerException(HandledException handledException) {
        if (processes.fetchCurrentProcess().isPresent()) {
            handleExceptionInProcess(handledException);
        } else {
            processes.executeInStandbyProcessForCurrentTenant("custom-event-handler",
                                                              () -> NLS.get("CustomEventHandler.customEvents"),
                                                              ignored -> handleExceptionInProcess(handledException));
        }
    }

    private void handleExceptionInProcess(HandledException handledException) {
        TaskContext.get()
                   .log(NLS.fmtr("CustomEventHandler.message")
                           .set("system", TaskContext.get().getSystemString())
                           .set("message", handledException.getMessage())
                           .format());
    }
}
