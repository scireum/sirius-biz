/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mapping;
import sirius.kernel.commons.Context;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Permits to extend existing {@link BaseImportHandler import handlers}.
 * <p>
 * This might be useful for customizations or {@link sirius.db.mixing.annotations.Mixin mixins} which need to extend the
 * import process beyond adding fields.
 */
public interface EntityImportHandlerExtender {

    /**
     * Enumerates additional loaders for the given handler and descriptor into the given collector.
     * <p>
     * If this extender provides additional ways of loading data into entities managed by the given handler / importer,
     * these loaders can be put into the given collector.
     *
     * @param handler         the handler which can be extended
     * @param descriptor      the descriptor determining what kind of entities are processed by the given handler
     * @param context         the surrounding importer context
     * @param loaderCollector the collector which consumes loader functions (reading from the given context and writing
     *                        into the given object).
     */
    default void collectLoaders(BaseImportHandler<BaseEntity<?>> handler,
                                EntityDescriptor descriptor,
                                ImporterContext context,
                                BiConsumer<Mapping, BiConsumer<Context, Object>> loaderCollector) {
        // intentionally left empty as not all extenders will customize all methods
    }

    /**
     * Enumerates additional mappings which should be automatically loaded by the given handler.
     *
     * @param handler         the handler which can be extended
     * @param descriptor      the descriptor determining what kind of entities are processed by the given handler
     * @param context         the surrounding importer context
     * @param mappingConsumer the consumer which collects additional mappings to load
     */
    default void collectAutoImportMappings(BaseImportHandler<BaseEntity<?>> handler,
                                           EntityDescriptor descriptor,
                                           ImporterContext context,
                                           Consumer<Mapping> mappingConsumer) {
        // intentionally left empty as not all extenders will customize all methods
    }
}
