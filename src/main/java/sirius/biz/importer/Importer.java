/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Context;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;

public class Importer implements Closeable {

    private ImportContext context = new ImportContext(this);

    public <E extends BaseEntity<?>> E load(Class<E> type, Context data) {
        return context.findHandler(type).load(data);
    }

    public <E extends BaseEntity<?>> Optional<E> tryFind(Class<E> type, Context data) {
        return context.findHandler(type).tryFind(data);
    }

    public <E extends BaseEntity<?>> Optional<E> tryFindInCache(Class<E> type, Context data) {
        return context.findHandler(type).tryFindInCache(data);
    }

    public <E extends BaseEntity<?>> E findOrFail(Class<E> type, Context data) {
        return context.findHandler(type).findOrFail(data);
//        return tryFind(type, data).orElseThrow(() -> Exceptions.createHandled().withSystemErrorMessage("Cannot find an instance for: %s of type %s", ));
    }

    public <E extends BaseEntity<?>> E findOrLoad(Class<E> type, Context data) {
        return context.findHandler(type).findOrLoad(data);
//        return tryFind(type, data).orElse(load(type, data));
    }

    public <E extends BaseEntity<?>> E findOrLoadAndCreate(Class<E> type, Context data) {
        return context.findHandler(type).findOrLoadAndCreate(data);
//        return tryFind(type, data).orElse(createOrUpdateNow(load(type, data)));
    }

    @SuppressWarnings("unchecked")
    public <E extends BaseEntity<?>> E createOrUpdateNow(E entity) {
        return context.findHandler((Class<E>) entity.getClass()).createOrUpdateNow(entity);
    }

    @SuppressWarnings("unchecked")
    public <E extends BaseEntity<?>> void createOrUpdateInBatch(E entity) {
        context.findHandler((Class<E>) entity.getClass()).createOrUpdateInBatch(entity);
    }

    @Override
    public void close() throws IOException {
        context.close();
    }
}
