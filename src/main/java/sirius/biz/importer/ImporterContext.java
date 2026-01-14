/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import sirius.biz.jobs.batch.ImportJob;
import sirius.biz.scripting.ScriptableEventHandler;
import sirius.db.jdbc.batch.BatchContext;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.PriorityParts;
import sirius.kernel.health.Exceptions;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides a shared context which is available to all {@link ImportHandler import handlers} of an {@link Importer}.
 */
public class ImporterContext {

    /**
     * Specifies the maximum number of post commit callbacks to keep around before a {@link #commit()} is forced.
     */
    private static final int MAX_POST_COMMIT_CALLBACKS = 256;

    @PriorityParts(ImportHandlerFactory.class)
    private static List<ImportHandlerFactory> factories;

    private final Importer importer;

    private BatchContext batchContext;

    private final Map<Class<?>, ImportHandler<?>> handlers = new HashMap<>();
    private final Map<Class<?>, ImportHelper> helpers = new HashMap<>();

    private final Cache<Tuple<Class<?>, String>, Object> localCache = Caffeine.newBuilder().maximumSize(256).build();

    private final List<Runnable> postCommitCallbacks = new ArrayList<>();

    private ScriptableEventHandler eventHandler = new ScriptableEventHandler();

    /**
     * Creates a new context for the given importer.
     *
     * @param importer the imported which this context was created
     */
    protected ImporterContext(Importer importer) {
        this.importer = importer;
    }

    /**
     * Defines the scriptable event handler to use for this context.
     *
     * @param eventHandler an instance of a {@link ScriptableEventHandler} which will be used to handle scriptable events
     * @return the context itself to allow fluent method calls
     */
    public ImporterContext withScriptableEventHandler(ScriptableEventHandler eventHandler) {
        this.eventHandler = eventHandler;
        return this;
    }

    /**
     * Resolves which {@link ImportHandler} to use for a given type.
     * <p>
     * Basically we iterate over all known {@link ImportHandlerFactory factories} (sorted by their priority ascending)
     * and use the first which returns <tt>true</tt> when invoking
     * {@link ImportHandlerFactory#accepts(Class, ImporterContext)}  with the given <tt>type</tt>. This factory is used
     * to create a new handler which is then kept in a local lookup table so that it is re-used for all subsequent
     * calls for this context and the given <tt>type</tt>.
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
        ImportHandler<E> result = (ImportHandler<E>) handlers.get(type);
        if (result == null) {
            // Note: computeIfAbsent cannot be used as a handler might reference other handlers. Those handler's
            // resolution would result in a ConcurrentModificationException in the #handlers Map.
            // This leads to cyclic handler dependencies not being resolvable here.
            result = (ImportHandler<E>) lookupHandler(type, type);
            handlers.put(type, result);
        }
        return result;
    }

    private ImportHandler<?> lookupHandler(Class<?> type, Class<?> baseType) {
        for (ImportHandlerFactory factory : factories) {
            if (factory.accepts(type, this)) {
                return factory.create(type, this);
            }
        }

        if (type.getSuperclass() != null && !type.getSuperclass().equals(type)) {
            return lookupHandler(type.getSuperclass(), baseType);
        } else {
            throw Exceptions.handle()
                            .withSystemErrorMessage("Cannot find an import handler for type: %s", baseType)
                            .handle();
        }
    }

    /**
     * Fetches or creates the {@link ImportHelper} of the given type.
     * <p>
     * These helpers are instantiated and kept around for each {@link Importer} and {@link ImporterContext} and can
     * therefore provide helper methods and carry along some state.
     *
     * @param type the type of helper to find
     * @param <H>  the generic helper type to find
     * @return the helper of the requested type
     * @throws sirius.kernel.health.HandledException if no appropriate helper is available
     */
    @SuppressWarnings("unchecked")
    public <H extends ImportHelper> H findHelper(Class<H> type) {
        ImportHelper result = helpers.get(type);
        if (result == null) {
            // Note: computeIfAbsent cannot be used as a helper might reference other helpers. Those helper's
            // resolution would result in a ConcurrentModificationException in the #helpers Map.
            // This leads to cyclic helper dependencies not being resolvable here.
            result = instantiateHelper(type);
            helpers.put(type, result);
        }
        return (H) result;
    }

    private ImportHelper instantiateHelper(Class<?> aClass) {
        try {
            return (ImportHelper) aClass.getConstructor(ImporterContext.class).newInstance(this);
        } catch (Exception _) {
            throw Exceptions.handle()
                            .withSystemErrorMessage("Cannot find or create the import helper of type: %s", aClass)
                            .handle();
        }
    }

    /**
     * Provides access to the importer for which this context was created.
     *
     * @return the imported associated with this context
     */
    public Importer getImporter() {
        return importer;
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
        try {
            commit();
        } catch (Exception exception) {
            Exceptions.handle()
                      .to(Importer.LOG)
                      .error(exception)
                      .withSystemErrorMessage("An error occurred while commiting an ImporterContext: %s (%s)")
                      .handle();
        }

        if (batchContext != null) {
            batchContext.close();
        }
    }

    /**
     * Adds a callback which is kept around until {@link #commit()} is called.
     * <p>
     * This will either happen when <b>commit</b> is called manually or when {@link #close()} is called.
     * <p>
     * Also, if there are more than {@link #MAX_POST_COMMIT_CALLBACKS} pending, this will invoke
     * {@link #commit()} directly.
     *
     * @param task the task to execute once all batched tasks have been executed.
     */
    public void addPostCommitCallback(Runnable task) {
        postCommitCallbacks.add(task);
        if (postCommitCallbacks.size() > MAX_POST_COMMIT_CALLBACKS) {
            commit();
        }
    }

    /**
     * Commits all open batch tasks of all {@link ImportHelper helpers} and {@link ImportHandler handlers}.
     * <p>
     * In most cases there is no need to call this method manually as all systems try to flush / commit data
     * when necessary or when the context is closed.
     */
    public void commit() {
        if (postCommitCallbacks.isEmpty()) {
            handlers.values().forEach(ImportHandler::commit);
            helpers.values().forEach(ImportHelper::commit);
        } else {
            while (!postCommitCallbacks.isEmpty()) {
                handlers.values().forEach(ImportHandler::commit);
                helpers.values().forEach(ImportHelper::commit);
                List<Runnable> executableCallbacks = new ArrayList<>(postCommitCallbacks);
                postCommitCallbacks.clear();
                executableCallbacks.forEach(Runnable::run);
            }
        }
    }

    /**
     * Determines if the underlying batch context has been initialized at all.
     * <p>
     * As some import might only target MongoDB, the batch context will remain untouched. As some reporting facilities
     * only call <tt>getBatchContext</tt> only to report its usage, this method can be used to prevent initializing
     * and empty context just to report it to the user.
     *
     * @return <tt>true</tt> if the batch context has been used, <tt>false</tt> otherwise
     * @see ImportJob#close()
     */
    public boolean hasBatchContext() {
        return batchContext != null && !batchContext.isEmpty();
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

    /**
     * Provides access to the scriptable event handler used by this context.
     *
     * @return the scriptable event handler used by this context
     */
    public ScriptableEventHandler getEventHandler() {
        return eventHandler;
    }
}
