/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.settings.Extension;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents the central class to manage and access {@link Blob blobs} and {@link Directory directories}.
 * <p>
 * When injecting always reference this class and let the system figure out the effective implementation which
 * either stores the metadata in SQL or in MongoDB.
 */
public abstract class BlobStorage {

    @Part
    protected StorageUtils util;

    private Map<String, BlobStorageSpace> spaceMap;

    protected Map<String, BlobStorageSpace> getSpaceMap() {
        if (spaceMap == null) {
            initializeSpaceMap();
        }

        return spaceMap;
    }

    private void initializeSpaceMap() {
        spaceMap = util.getStorageSpaces(StorageUtils.ConfigScope.LAYER2)
                       .stream()
                       .map(this::createSpace)
                       .collect(Collectors.toMap(BlobStorageSpace::getName, Function.identity()));
    }

    /**
     * Creates the storage spaceusing the given config.
     *
     * @param config the settings to use when creating the space
     * @return a wrapper which is used to access the objects stored in the storage space
     */
    protected abstract BlobStorageSpace createSpace(Extension config);

    /**
     * Determines if the storage space with the given name is known.
     *
     * @param space the name of the space to check
     * @return <tt>true</tt> if the space is known, <tt>false</tt> otherwise
     */
    public boolean isKnown(String space) {
        return getSpaceMap().containsKey(space);
    }

    /**
     * Returns the storage space with the given name.
     *
     * @param name the name of the space
     * @return a wrapper which is used to access the objects stored in the storage space
     * @throws sirius.kernel.health.HandledException if an unknown storage space is requested.
     */
    public BlobStorageSpace getSpace(String name) {
        BlobStorageSpace result = getSpaceMap().get(name);
        if (result == null) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .withSystemErrorMessage("Layer2: Cannot find a configuration for space '%s'"
                                                    + ". Please verify the system configuration.", name)
                            .handle();
        }

        return result;
    }

    /**
     * Enumerates all known storage spaces.
     *
     * @return a stream which enumerates all configured blob storage spaces
     */
    public Stream<BlobStorageSpace> getSpaces() {
        return getSpaceMap().values().stream();
    }
}
