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
 * Provides some metadata for an InfoGraphDB table.
 */
public class IDBTableInfo {

    private final JupiterConnector connection;
    private final String name;
    private final long rows;
    private final long allocatedMemory;
    private final long numQueries;
    private final long numScanQueries;
    private final long numScans;

    /**
     * Creates a new metadata info for a given table.
     *
     * @param connection      the underlying connection
     * @param name            the name of the table
     * @param rows            the number of rows in the table
     * @param allocatedMemory the allocated memory in bytes
     * @param numQueries      the number of queries executed so far
     * @param numScanQueries  the number of scan queries executed so far
     * @param numScans        the number of table scans executed so far
     */
    public IDBTableInfo(JupiterConnector connection,
                        String name,
                        long rows,
                        long allocatedMemory,
                        long numQueries,
                        long numScanQueries,
                        long numScans) {
        this.connection = connection;
        this.name = name;
        this.rows = rows;
        this.allocatedMemory = allocatedMemory;
        this.numQueries = numQueries;
        this.numScanQueries = numScanQueries;
        this.numScans = numScans;
    }

    /**
     * Returns the name of the table.
     *
     * @return the name of the table
     */
    public String getName() {
        return name;
    }

    /**
     * Provides access to the actual table.
     *
     * @return the wrapped used to actually query the table
     */
    public IDBTable table() {
        return new IDBTable(connection, name);
    }

    /**
     * Returns the number of rows in the table.
     *
     * @return the number of rows in the table
     */
    public long getRows() {
        return rows;
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
     * @return the number of queries performed against the table
     */
    public long getNumQueries() {
        return numQueries;
    }

    /**
     * Returns the number of lookup queries which required a table scan.
     *
     * @return the number of scan queries executed so far
     */
    public long getNumScanQueries() {
        return numScanQueries;
    }

    /**
     * Returns the number of table scans which have been performed.
     *
     * @return the number of table scans
     */
    public long getNumScans() {
        return numScans;
    }

    @Override
    public String toString() {
        return name + "(" + NLS.toUserString(rows) + ", " + NLS.formatSize(allocatedMemory) + ")";
    }
}
