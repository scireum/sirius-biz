/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

/**
 * A marker interface which is used by the <tt>optimistic lock</tt> algorithms in {@link BasicBlobStorageSpace}.
 */
public interface OptimisticCreate {

    /**
     * Determines if this entity has already been committed.
     * <p>
     * Being committed means that this entity has be checked by the verification step of the various
     * <tt>optimistic lock</tt> algorithms used in {@link BasicBlobStorageSpace}. These entities can be used
     * and referenced by other methods. If an entity is not committed yet, it must not be used as it might be
     * deleted by the optimistic locking approach.
     *
     * @return <tt>true</tt> if the entity is committed, <tt>false</tt> otherwise
     */
    boolean isCommitted();
}
