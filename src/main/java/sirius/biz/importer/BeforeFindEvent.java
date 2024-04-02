/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import sirius.db.mixing.Entity;
import sirius.kernel.commons.Context;

/**
 * Triggered within {@link sirius.biz.importer.ImportHandler#tryFind(Context)} before the entity is actually being
 * looked up.
 *
 * @param <E> the type of entity being looked up
 */
public class BeforeFindEvent<E extends Entity> extends ContextScriptableEvent<E> {

    /**
     * Creates a new event for the given entity type, context and import context.
     *
     * @param entityType      the type of entities being looked up
     * @param context         the context to read data from. Note that this can and should be modified by the handler,
     *                        as this is the whole purpose of this event anyway.
     * @param importerContext the import context which can be used to access other handlers / the importer itself
     */
    public BeforeFindEvent(Class<E> entityType, Context context, ImporterContext importerContext) {
        super(entityType, context, importerContext);
    }
}
