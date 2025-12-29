/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import sirius.biz.scripting.TypedScriptableEvent;
import sirius.db.mixing.Entity;
import sirius.pasta.noodle.sandbox.NoodleSandbox;

import java.util.function.Consumer;

/**
 * Triggered within {@link sirius.biz.importer.txn.ImportTransactionHelper#deleteUnmarked(Class, Consumer, Consumer)}
 * before an entity is deleted.
 *
 * @param <E> the type of entity being deleted
 */
public class BeforeDeleteEvent<E extends Entity> extends TypedScriptableEvent<E> {
    private final E entity;
    private final ImporterContext importerContext;

    /**
     * Creates a new event for the given entity
     *
     * @param entity          the entity to delete
     * @param importerContext the import context which can be used to access other handlers / the importer itself
     */
    public BeforeDeleteEvent(E entity, ImporterContext importerContext) {
        this.importerContext = importerContext;
        this.entity = entity;
    }

    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public E getEntity() {
        return entity;
    }

    @Override
    public String toString() {
        return "BeforeDeleteEvent: "
               + getType().getName()
               + " with entity "
               + entity
               + "(ID: "
               + entity.getIdAsString()
               + ")";
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<E> getType() {
        return (Class<E>) entity.getClass();
    }

    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public ImporterContext getImporterContext() {
        return importerContext;
    }
}
