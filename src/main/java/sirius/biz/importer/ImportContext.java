/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import sirius.db.jdbc.batch.BatchContext;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.PriorityParts;
import sirius.kernel.health.Exceptions;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides a shared context which is available to all {@link ImportHandler import handlers} of an {@link Importer}.
 */
public class ImportContext {

    private Importer importer;
    private BatchContext batchContext;

    @PriorityParts(ImportHandlerFactory.class)
    private static List<ImportHandlerFactory> factories;

    private Map<Class<?>, ImportHandler<?>> handlers = new HashMap<>();
    private Cache<Tuple<Class<?>, String>, Object> localCache = CacheBuilder.newBuilder().maximumSize(256).build();

    /**
     * Creates a new context for the given importer.
     *
     * @param importer the imported which which this context was created
     */
    protected ImportContext(Importer importer) {
        this.importer = importer;
    }

    /**
     * Resolves which {@link ImportHandler} to use for a given type.
     * <p>
     * Basically we iterate over all known {@link ImportHandlerFactory factories} (sorted by their priority ascending)
     * and use the first which returns <tt>true</tt> when invoking {@link ImportHandlerFactory#accepts(Class)} with the
     * given <tt>type</tt>. This factory is used to create a new handler which is then kept in a local lookup table
     * so that it is re-used for all subsequent calls for this context and the given <tt>type</tt>.
     * <p>
     * If no factory matches, we repeat the search using the superclass.
     *
     * @param type the entity type to find the appropriate handler for.
     * @param <E>  the generic type of the entity class
     * @return the appropriate handler for this type
     * @throws sirius.kernel.health.HandledException if no appropriate handler is available
     */
    @SuppressWarnings("unchecked")
    public <E extends BaseEntity<?>> ImportHandler<E> findHandler(Class<E> type) {
        return (ImportHandler<E>) handlers.computeIfAbsent(type, this::lookupHandler);
    }

    @SuppressWarnings("unchecked")
    private ImportHandler<?> lookupHandler(Class<?> type) {
        for (ImportHandlerFactory factory : factories) {
            if (factory.accepts(type)) {
                return factory.create(type, this);
            }
        }

        if (type.getSuperclass() != null && !type.getSuperclass().equals(type)) {
            return lookupHandler(type.getSuperclass());
        } else {
            throw Exceptions.createHandled()
                            .withSystemErrorMessage("Cannot find an import handler for type: %s", type)
                            .handle();
        }
    }

    /**
     * Provides access to the local cache which can be utilized by {@link ImportHandler#tryFindInCache(Context)}.
     *
     * @return the local cache of this importer
     */
    public Cache<Tuple<Class<?>, String>, Object> getLocalCache() {
        return localCache;
    }

    /**
     * Closes this context and completes all running batches.
     *
     * @throws IOException in case of an io error. Most probably this won't happen, as we only use
     *                     {@link java.io.Closeable} to be supported by the IDEs sanity checks.
     */
    protected void close() throws IOException {
        if (batchContext != null) {
            batchContext.close();
        }
    }

    /**
     * Provides access to the underlying JDBC {@link BatchContext} which supports batches and prepared statements.
     *
     * @return the batch context used by the associated importer
     */
    public BatchContext getBatchContext() {
        if (batchContext == null) {
            this.batchContext = new BatchContext(() -> "Batch Context of: " + importer.name, Duration.ofHours(10));
        }

        return batchContext;
    }
}
