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
import java.util.stream.Collectors;

/**
 * Permits to display and manage the repository contents of an attached Jupiter instance.
 * <p>
 * This is commonly obtained via {@link JupiterConnector#repository()}.
 */
public class Repository {

    private static final JupiterCommand CMD_SCAN = new JupiterCommand("REPO.SCAN");
    private static final JupiterCommand CMD_FETCH = new JupiterCommand("REPO.FETCH");
    private static final JupiterCommand CMD_FETCH_FORCED = new JupiterCommand("REPO.FETCH_FORCED");
    private static final JupiterCommand CMD_STORE = new JupiterCommand("REPO.STORE");
    private static final JupiterCommand CMD_DELETE = new JupiterCommand("REPO.DELETE");
    private static final JupiterCommand CMD_LIST = new JupiterCommand("REPO.LIST");
    private static final JupiterCommand CMD_INC_EPOCH = new JupiterCommand("REPO.INC_EPOCH");
    private static final JupiterCommand CMD_EPOCHS = new JupiterCommand("REPO.EPOCHS");

    private final JupiterConnector connection;

    /**
     * Creates a new repository wrapper for the given connection.
     *
     * @param connection the connection to create a repository wrapper for
     */
    public Repository(JupiterConnector connection) {
        this.connection = connection;
    }

    /**
     * Forces a scan of the repository contents on disk.
     * <p>
     * This is usually not required, as the repository is scanned automatically. The only reason to issue a forced
     * scan is, if an external program has changed the repository contents.
     */
    public void forceScan() {
        connection.execDirect(() -> "REPO.SCAN", jupiter -> {
            jupiter.sendCommand(CMD_SCAN);
            jupiter.getStatusCodeReply();
        });
    }

    /**
     * Instructs the repository to fetch the data of the given URL into the given file.
     *
     * @param filename the file/path to store
     * @param url      the source of the data to load
     * @param force    <tt>true</tt> if a fetch should be performed even if a local file already exists and seems up to
     *                 date, <tt>false</tt> otherwise
     */
    public void fetchUrl(String filename, String url, boolean force) {
        connection.execDirect(() -> "REPO.FETCH", jupiter -> {
            jupiter.sendCommand(force ? CMD_FETCH_FORCED : CMD_FETCH, filename, url);
            jupiter.getStatusCodeReply();
        });
    }

    /**
     * Directly stores the given data in the given file.
     *
     * @param filename the file/path to store the data in
     * @param data     the actual data to store
     */
    public void store(String filename, String data) {
        connection.execDirect(() -> "REPO.STORE", jupiter -> {
            jupiter.sendCommand(CMD_STORE, filename, data);
            jupiter.getStatusCodeReply();
        });
    }

    /**
     * Deletes the given repository file.
     *
     * @param filename the file/path to delete
     */
    public void delete(String filename) {
        connection.execDirect(() -> "REPO.DELETE", jupiter -> {
            jupiter.sendCommand(CMD_DELETE, filename);
            jupiter.getStatusCodeReply();
        });
    }

    /**
     * Lists the current repository contents.
     *
     * @return the list of files in the repository
     */
    public List<RepositoryFile> list() {
        return connection.queryDirect(() -> "REPO.LIST", jupiter -> {
            jupiter.sendCommand(CMD_LIST, "raw");
            return jupiter.getObjectMultiBulkReply().stream().map(this::parseMetadataRow).collect(Collectors.toList());
        });
    }

    private RepositoryFile parseMetadataRow(Object obj) {
        Values row = Jupiter.readArray(obj);
        return new RepositoryFile(Jupiter.readString(row.at(0)),
                                  row.at(1).asLong(0),
                                  Jupiter.readLocalDateTime(row.at(2)));
    }

    /**
     * Increments the foreground and background epoch in the Jupiter instance.
     * <p>
     * This can be used to determine if the background system of the Jupiter repository is idle (has completed all
     * previously scheduled tasks).
     * <p>
     * After invoking this, one has to call {@link #isEpochInSync()} and wait until this returns <tt>true</tt>.
     */
    public void requestEpoch() {
        connection.execDirect(() -> "REPO.INC_EPOCH", jupiter -> {
            jupiter.sendCommand(CMD_INC_EPOCH);
            jupiter.getStatusCodeReply();
        });
    }

    /**
     * Determines if the foreground epoch and background epoch of Jupiter are equal.
     * <p>
     * If previously {@link #requestEpoch()} has been called, this can be used to guarantee, that the background
     * actor of Jupiter is idle/has completed all tasks.
     *
     * @return <tt>true</tt> if the epochs are equal (the background system is idle), <tt>false</tt> otherwise
     */
    public boolean isEpochInSync() {
        return connection.queryDirect(() -> "REPO.EPOCHS", jupiter -> {
            jupiter.sendCommand(CMD_EPOCHS);
            Values epochs = Values.of(jupiter.getIntegerMultiBulkReply());
            return epochs.at(0).asLong(0) == epochs.at(1).asLong(-1);
        });
    }
}
