/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1;

import sirius.biz.storage.util.DerivedSpaceInfo;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.settings.Extension;

/**
 * Provides the public API to the Layer 1 of the storage framework.
 * <p>
 * This layer operates on <b>objects</b> which are stored in <b>spaces</b> much like Amazon S3 does. However,
 * next to S3 compatible stores it also supports an emulation layer which stores all data in the local file system.
 * <p>
 * Additionally a replication framework is available which can synchronize all objects in one storage space
 * with a second one.
 * <p>
 * Note that this layer neither keeps track of the objects being stored nor known paths, files or directory. This
 * is completely handled in the layer 2.
 */
@Register(classes = PhysicalObjectStorage.class)
public class PhysicalObjectStorage {

    /**
     * Contains the config attribute which determines which storage engine to use for a space.
     */
    public static final String CONFIG_KEY_LAYER1_ENGINE = "engine";

    @Part
    private StorageUtils utils;

    @Part
    private GlobalContext globalContext;

    @ConfigValue("storage.layer1.spaces.default.engine")
    private String defaultEngine;

    private DerivedSpaceInfo<PhysicalStorageEngine> engines = new DerivedSpaceInfo<>(CONFIG_KEY_LAYER1_ENGINE,
                                                                                     StorageUtils.ConfigScope.LAYER1,
                                                                                     this::resolveStorageEngine);

    private PhysicalStorageEngine resolveStorageEngine(Extension extension) {
        PhysicalStorageEngine result =
                globalContext.getPart(extension.getString(CONFIG_KEY_LAYER1_ENGINE), PhysicalStorageEngine.class);
        if (result != null) {
            return result;
        }

        StorageUtils.LOG.WARN("Layer 1: An invalid storage engine (%s) was given for space '%s' - defaulting to '%s'",
                              extension.getString(CONFIG_KEY_LAYER1_ENGINE),
                              extension.getId(),
                              defaultEngine);
        return globalContext.getPart(defaultEngine, PhysicalStorageEngine.class);
    }

    /**
     * Determines if the storage space with the given name is known.
     *
     * @param space the name of the space to check
     * @return <tt>true</tt> if the space is known, <tt>false</tt> otherwise
     */
    public boolean isKnown(String space) {
        return engines.contains(space);
    }

    /**
     * Returns the storage space with the given name.
     *
     * @param name the name of the space
     * @return a wrapper which is used to access the objects stored in the storage space
     * @throws sirius.kernel.health.HandledException if an unknow storage space is requested.
     */
    public PhysicalStorageSpace getSpace(String name) {
        return new PhysicalStorageSpace(name, engines.get(name));
    }
}
