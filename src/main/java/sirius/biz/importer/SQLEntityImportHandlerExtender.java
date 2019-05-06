/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import sirius.db.jdbc.SQLEntity;
import sirius.db.jdbc.batch.FindQuery;
import sirius.db.mixing.EntityDescriptor;

import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Permits to extend existing {@link SQLEntityImportHandler import handlers}.
 * <p>
 * This might be useful for customizations or {@link sirius.db.mixing.annotations.Mixin mixins} which need to extend the
 * import process beyond adding fields.
 *
 * @param <E> the type of entities being processed by the import handler to be extended
 */
public class SQLEntityImportHandlerExtender<E extends SQLEntity> implements EntityImportHandlerExtender {

    /**
     * Enumerates additional {@link FindQuery find queries} for the given handler.
     *
     * @param handler       the handler which can be extended
     * @param descriptor    the descriptor determining what kind of entities are processed by the given handler
     * @param context       the surrounding importer context
     * @param queryConsumer the collector which consumes additiona find queries
     */
    public void collectFindQueries(SQLEntityImportHandler<E> handler,
                                   EntityDescriptor descriptor,
                                   ImporterContext context,
                                   BiConsumer<Predicate<E>, Supplier<FindQuery<E>>> queryConsumer) {
        // intentionally left empty as not all extenders will customize all methods
    }
}
