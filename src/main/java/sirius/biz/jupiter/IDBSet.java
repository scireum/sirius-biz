/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jupiter;

import sirius.kernel.commons.Tuple;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps an InfoGraphDB table of an attached Jupiter instance.
 */
public class IDBSet {

    private static final JupiterCommand CMD_CONTAINS = new JupiterCommand("IDB.CONTAINS");
    private static final JupiterCommand CMD_INDEX_OF = new JupiterCommand("IDB.INDEX_OF");

    private final JupiterConnector jupiter;
    private final String name;

    /**
     * Creates a new wrapper for a given Jupiter instance and set name.
     *
     * @param jupiter the jupiter connection to wrap
     * @param name    the name of the set to wrap
     */
    public IDBSet(JupiterConnector jupiter, String name) {
        this.jupiter = jupiter;
        this.name = name;
    }

    /**
     * Checks if the set contains the key.
     *
     * @param key the key to check for
     * @return <tt>true</tt> if the key is contained in the set, <tt>false</tt> otherwise
     */
    public boolean contains(String key) {
        return jupiter.query(this::containsOp, redis -> {
            redis.sendCommand(CMD_CONTAINS, name, key);

            return redis.getIntegerReply() == 1;
        });
    }

    private String containsOp() {
        return CMD_CONTAINS.toString() + " " + name;
    }

    /**
     * Checks if the set contains any of the given keys.
     *
     * @param keys the keys to check for
     * @return <tt>true</tt> if any key is contained in the set, <tt>false</tt> otherwise
     */
    public boolean containsAny(String... keys) {
        return jupiter.query(this::containsOp, redis -> {
            String[] params = new String[keys.length + 1];
            params[0] = name;
            System.arraycopy(keys, 0, params, 1, keys.length);
            redis.sendCommand(CMD_CONTAINS, params);

            return redis.getIntegerMultiBulkReply().stream().anyMatch(result -> result == 1);
        });
    }

    /**
     * Checks if the set contains all of the given keys.
     *
     * @param keys the keys to check for
     * @return <tt>true</tt> if all keys is contained in the set, <tt>false</tt> otherwise
     */
    public boolean containsAll(String... keys) {
        return jupiter.query(this::containsOp, redis -> {
            String[] params = new String[keys.length + 1];
            params[0] = name;
            System.arraycopy(keys, 0, params, 1, keys.length);
            redis.sendCommand(CMD_CONTAINS, params);

            return redis.getIntegerMultiBulkReply().stream().allMatch(result -> result == 1);
        });
    }

    /**
     * Determines the position of the key within the set.
     *
     * @param key the key to check for
     * @return the one-based position within the set or 0 if the key isn't part of the set
     */
    public int indexOf(String key) {
        return jupiter.query(this::indexOfOp, redis -> {
            redis.sendCommand(CMD_INDEX_OF, name, key);

            return redis.getIntegerReply().intValue();
        });
    }

    private String indexOfOp() {
        return CMD_INDEX_OF.toString() + " " + name;
    }

    /**
     * Determines all indices of the given keys.
     *
     * @param keys the keys to check for
     * @return a list of all keys along with their position in the set
     */
    public List<Tuple<String, Integer>> indexOf(String... keys) {
        return jupiter.query(this::indexOfOp, redis -> {
            redis.sendCommand(CMD_INDEX_OF, keys);

            List<Long> indices = redis.getIntegerMultiBulkReply();
            List<Tuple<String, Integer>> results = new ArrayList<>(keys.length);
            for (int i = 0; i < keys.length; i++) {
                results.add(Tuple.create(keys[i], indices.get(i).intValue()));
            }

            return results;
        });
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "IDB Set: " + name + " in: " + jupiter;
    }
}
