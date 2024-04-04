/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import sirius.biz.scripting.TypedScriptableEvent;
import sirius.kernel.commons.Context;
import sirius.pasta.noodle.sandbox.NoodleSandbox;

/**
 * Provides a base implementation for all events which are scriptable and have a context / import context
 *
 * @param <E> the generic type of entities being handled
 */
public abstract class ContextScriptableEvent<E> extends TypedScriptableEvent<E> {

    private final Class<E> entityType;
    private final Context context;
    private final ImporterContext importerContext;

    /**
     * Creates a new event for the given entity type, context and import context.
     *
     * @param entityType      the type of entity being handled
     * @param context         the context to read data from. Note that this can and should be modified by the handler,
     *                        as this is the whole purpose of this event anyway.
     * @param importerContext the import context which can be used to access other handlers / the importer itself
     */
    @SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType")
    protected ContextScriptableEvent(Class<E> entityType, Context context, ImporterContext importerContext) {
        this.entityType = entityType;
        this.context = context;
        this.importerContext = importerContext;
    }

    /**
     * Returns the context which contains the data of the current event.
     * <p>
     * Note that this is mutable by design, as the handler will most probably want to modify the context
     * before it is processed by the import handler
     *
     * @return the context containing the actual data being imported
     */
    @SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType")
    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public Context getContext() {
        return context;
    }

    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public ImporterContext getImporterContext() {
        return importerContext;
    }

    @Override
    public Class<E> getType() {
        return entityType;
    }
}
