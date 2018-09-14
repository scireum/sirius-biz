/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster;

/**
 * Determines the way a background process is synchronized by the {@link NeighborhoodWatch}.
 */
public enum SynchronizeType {

    /**
     * Disables the process on this node completely.
     */
    DISABLED,

    /**
     * Enforces cluster-wide locks so that this process only runs on one node in parallel.
     */
    CLUSTER,

    /**
     * Runs is process locally without synchronizing against cluster members.
     */
    LOCAL

}
