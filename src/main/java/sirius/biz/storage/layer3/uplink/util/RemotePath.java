/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink.util;

import sirius.biz.storage.layer3.MutableVirtualFile;
import sirius.kernel.commons.Strings;

/**
 * Wraps a path to be attached to a {@link MutableVirtualFile} when providing an uplink.
 */
public class RemotePath {

    private final String path;

    /**
     * Generates a new wrapper for the given path.
     *
     * @param path the path to wrap
     */
    public RemotePath(String path) {
        this.path = path;
    }

    /**
     * Generates a sub-path for the given file / directory.
     *
     * @param file the file or directory to represent
     * @return a path which points to the given child file in this path
     */
    public RemotePath child(String file) {
        if (Strings.isFilled(path) && path.endsWith("/")) {
            return new RemotePath(this.path + file);
        } else {
            return new RemotePath(this.path + "/" + file);
        }
    }

    /**
     * Returns the wrapped path.
     *
     * @return the wrapped path
     */
    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return path;
    }
}
