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

import javax.annotation.Nullable;
import java.util.function.BiConsumer;
import java.util.function.Function;

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
    default void collectLoaders(BaseImportHandler<? extends BaseEntity<?>> handler,
                                EntityDescriptor descriptor,
                                ImporterContext context,
                                BiConsumer<Mapping, BiConsumer<Context, Object>> loaderCollector) {
        // intentionally left empty as not all extenders will customize all methods
    }

    /**
     * Permits to provide an extractor for the given handler and field.
     *
     * @param handler       the handler which can be extended
     * @param descriptor    the descriptor determining what kind of entities are processed by the given handler
     * @param context       the surrounding importer context
     * @param fieldToExport the field to export
     * @param <E>           the generic entity type to be exported
     * @return a function which extracts the field to be exported from a given entity
     * @see BaseImportHandler#createExtractor(String)
     */
    @Nullable
    default <E extends BaseEntity<?>> Function<E, Object> createExtractor(BaseImportHandler<E> handler,
                                                                          EntityDescriptor descriptor,
                                                                          ImporterContext context,
                                                                          String fieldToExport) {
        // intentionally left empty as not all extenders will customize all methods
        return null;
    }

    /**
     * Permits to add additional default exports for the given handler.
     *
     * @param handler    the handler which can be extended
     * @param descriptor the descriptor determining what kind of entities are processed by the given handler
     * @param collector  a collector to be supplied with additional columns to be exported
     * @see BaseImportHandler#getDefaultExportMapping()
     */
    default void collectDefaultExportableMappings(BaseImportHandler<? extends BaseEntity<?>> handler,
                                                  EntityDescriptor descriptor,
                                                  BiConsumer<Integer, Mapping> collector) {
        // intentionally left empty as not all extenders will customize all methods
    }

    /**
     * Permits to add additional exports for the given handler.
     *
     * @param handler    the handler which can be extended
     * @param descriptor the descriptor determining what kind of entities are processed by the given handler
     * @param collector  a collector to be supplied with additional columns to be exported
     * @see BaseImportHandler#getExportableMappings()
     */
    default void collectExportableMappings(BaseImportHandler<? extends BaseEntity<?>> handler,
                                           EntityDescriptor descriptor,
                                           BiConsumer<Integer, Mapping> collector) {
        // intentionally left empty as not all extenders will customize all methods
    }
}
