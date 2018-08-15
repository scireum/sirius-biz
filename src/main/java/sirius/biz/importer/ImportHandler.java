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

import java.util.Optional;

public interface ImportHandler<E extends BaseEntity<?>> {

    E load(Context data);

    Optional<E> tryFind(Context data);

    Optional<E> tryFindInCache(Context data);

    E findOrFail(Context data);

    E findOrLoad(Context data);

    E findOrLoadAndCreate(Context data);

    E createOrUpdateNow(E entity);

    void createOrUpdateInBatch(E entity);
}
