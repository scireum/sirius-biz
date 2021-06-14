/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jupiter;

import sirius.kernel.nls.NLS;

/**
 * Provides some metadata for an InfoGraphDB set.
 */
public class IDBSetInfo {

    private final JupiterConnector connection;
    private final String name;
    private final long entries;
    private final long allocatedMemory;
    private final long numQueries;

    /**
     * Creates a new metadata info for a given set.
     *
     * @param connection      the underlying connection
     * @param name            the name of the set
     * @param entries         the number of entries in the set
     * @param allocatedMemory the allocated memory in bytes
     * @param numQueries      the number of queries executed so far
     */
    public IDBSetInfo(JupiterConnector connection, String name, long entries, long allocatedMemory, long numQueries) {
        this.connection = connection;
        this.name = name;
        this.entries = entries;
        this.allocatedMemory = allocatedMemory;
        this.numQueries = numQueries;
    }

    /**
     * Returns the name of the set.
     *
     * @return the name of the set
     */
    public String getName() {
        return name;
    }

    /**
     * Provides access to the actual set.
     *
     * @return the wrapped used to actually query the set
     */
    public IDBSet set() {
        return new IDBSet(connection, name);
    }

    /**
     * Returns the number of entries in the set.
     *
     * @return the number of entries in the set
     */
    public long getEntries() {
        return entries;
    }

    /**
     * Returns the allocated memory used by the table and its index.
     *
     * @return the allocated memory in bytes
     */
    public long getAllocatedMemory() {
        return allocatedMemory;
    }

    /**
     * Returns the number of queries executed so far.
     *
     * @return the number of queries performed against the set
     */
    public long getNumQueries() {
        return numQueries;
    }


    @Override
    public String toString() {
        return name + "(" + NLS.toUserString(entries) + ", " + NLS.formatSize(allocatedMemory) + ")";
    }
}
