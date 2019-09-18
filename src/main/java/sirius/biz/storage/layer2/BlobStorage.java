/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.biz.storage.util.DerivedSpaceInfo;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.di.std.Part;

import java.util.stream.Stream;

/**
 * Represents the central class to manage and access {@link Blob blobs} and {@link Directory directories}.
 * <p>
 * When injecting always reference this class and let the system figure out the effective implementation which
 * either stores the metadata in SQL or in MongoDB.
 */
public abstract class BlobStorage {

    /**
     * Determines if the space is browsable.
     * <p>
     * A browsable space has a root directory per tenant and can be viewed and used like a regular file system
     * via the {@link L3Uplink uplink}.
     */
    public static final String CONFIG_KEY_LAYER2_BROWSABLE = "browsable";

    /**
     * Determines if the space is mounted as readonly.
     * <p>
     * A {@link #browsable} space can me mounted readonly which will suppress all mutating calls via the Layer 3
     * {@link L3Uplink uplink}.
     */
    public static final String CONFIG_KEY_LAYER2_READONLY = "readonly";

    @Part
    protected StorageUtils util;

    protected DerivedSpaceInfo<Boolean> browsable = new DerivedSpaceInfo<>(CONFIG_KEY_LAYER2_BROWSABLE,
                                                                           StorageUtils.ConfigScope.LAYER2,
                                                                           extension -> extension.get(
                                                                                   CONFIG_KEY_LAYER2_BROWSABLE)
                                                                                                 .asBoolean());

    protected DerivedSpaceInfo<Boolean> readonly = new DerivedSpaceInfo<>(CONFIG_KEY_LAYER2_READONLY,
                                                                          StorageUtils.ConfigScope.LAYER2,
                                                                          extension -> extension.get(
                                                                                  CONFIG_KEY_LAYER2_READONLY)
                                                                                                .asBoolean());

    /**
     * Determines if the storage space with the given name is known.
     *
     * @param space the name of the space to check
     * @return <tt>true</tt> if the space is known, <tt>false</tt> otherwise
     */
    public boolean isKnown(String space) {
        return browsable.contains(space);
    }

    /**
     * Returns the storage space with the given name.
     *
     * @param name the name of the space
     * @return a wrapper which is used to access the objects stored in the storage space
     * @throws sirius.kernel.health.HandledException if an unknown storage space is requested.
     */
    public abstract BlobStorageSpace getSpace(String name);

    /**
     * Enumerates all known storage spaces.
     *
     * @return a stream which enumerates all configured blob storage spaces
     */
    public Stream<BlobStorageSpace> getSpaces() {
        return util.getStorageSpaces(StorageUtils.ConfigScope.LAYER2)
                   .stream()
                   .map(ext -> ext.getId())
                   .map(this::getSpace);
    }
}
