/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jupiter;

import sirius.kernel.commons.Values;

import java.util.List;

/**
 * Provides access to the InfoGraphDB of a Jupiter connection.
 * <p>
 * This is commonly obtained via {@link JupiterConnector#idb()}.
 */
public class InfoGraphDB {

    private static final JupiterCommand CMD_SHOW_TABLES = new JupiterCommand("IDB.SHOW_TABLES");
    private static final JupiterCommand CMD_SHOW_SETS = new JupiterCommand("IDB.SHOW_SETS");

    private final JupiterConnector connection;

    /**
     * Creates a new wrapper based on the given connection.
     *
     * @param connection the connection to create the wrapper for
     */
    public InfoGraphDB(JupiterConnector connection) {
        this.connection = connection;
    }

    /**
     * Provides a query wrapper for the given table.
     *
     * @param name the name of the table to access
     * @return a wrapper to query an InfoGraphDB table
     */
    public IDBTable table(String name) {
        return new IDBTable(connection, name);
    }

    /**
     * Lists all known tables in the connected InfoGraphDB.
     *
     * @return a list of all known tables
     */
    public List<IDBTableInfo> showTables() {
        return connection.query(() -> "IDB.SHOW_TABLES", redis -> {
            redis.sendCommand(CMD_SHOW_TABLES, "raw");
            return redis.getObjectMultiBulkReply().stream().map(this::parseTableMetadataRow).toList();
        });
    }

    private IDBTableInfo parseTableMetadataRow(Object obj) {
        Values row = Jupiter.readArray(obj);
        return new IDBTableInfo(connection,
                                Jupiter.readString(row.at(0)),
                                row.at(1).asLong(0),
                                row.at(2).asLong(0),
                                row.at(3).asLong(0),
                                row.at(4).asLong(0),
                                row.at(5).asLong(0));
    }

    /**
     * Provides a query wrapper for the given set.
     *
     * @param name the name of the set to access
     * @return a wrapper to query an InfoGraphDB set
     */
    public IDBSet set(String name) {
        return new IDBSet(connection, name);
    }

    /**
     * Lists all known sets in the connected InfoGraphDB.
     *
     * @return a list of all known sets
     */
    public List<IDBSetInfo> showSets() {
        return connection.query(() -> "IDB.SHOW_SETS", redis -> {
            redis.sendCommand(CMD_SHOW_SETS, "raw");
            return redis.getObjectMultiBulkReply().stream().map(this::parseSetMetadataRow).toList();
        });
    }

    private IDBSetInfo parseSetMetadataRow(Object obj) {
        Values row = Jupiter.readArray(obj);
        return new IDBSetInfo(connection,
                              Jupiter.readString(row.at(0)),
                              row.at(1).asLong(0),
                              row.at(2).asLong(0),
                              row.at(3).asLong(0));
    }
}
