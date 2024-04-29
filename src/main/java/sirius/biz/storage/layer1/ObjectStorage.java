/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1;

import sirius.biz.storage.layer1.replication.ReplicationManager;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.settings.Extension;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

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
@Register(classes = ObjectStorage.class)
public class ObjectStorage {

    /**
     * Contains the config attribute which determines which storage engine to use for a space.
     */
    public static final String CONFIG_KEY_LAYER1_ENGINE = "engine";
    /**
     * Contains the config attribute which determines which {@link ObjectStoraceSpaceFactory} to use for a space.
     */
    public static final String CONFIG_KEY_LAYER1_COMPRESSION = "compression";
    /**
     * Contains the config attribute which determines which {@link sirius.biz.storage.layer1.transformer.CipherFactory}
     * to use for a space.
     */
    public static final String CONFIG_KEY_LAYER1_CIPHER = "cipher";

    @Part
    private StorageUtils utils;

    @Part
    private GlobalContext globalContext;

    @Part
    @Nullable
    private ReplicationManager replicationManager;

    @ConfigValue("storage.layer1.spaces.default.engine")
    private String defaultEngine;

    private Map<String, ObjectStorageSpace> spaceMap;

    protected Map<String, ObjectStorageSpace> getSpaceMap() {
        if (spaceMap == null) {
            spaceMap = initializeSpaceMap();
        }

        return spaceMap;
    }

    private Map<String, ObjectStorageSpace> initializeSpaceMap() {
        Map<String, ObjectStorageSpace> result = utils.getStorageSpaces(StorageUtils.ConfigScope.LAYER1)
                                                      .stream()
                                                      .map(this::createSpace)
                                                      .filter(Objects::nonNull)
                                                      .collect(Collectors.toMap(ObjectStorageSpace::getName,
                                                                                Function.identity()));

        replicationManager.initializeReplication(result);

        return result;
    }

    private ObjectStorageSpace createSpace(Extension extension) {
        try {
            ObjectStoraceSpaceFactory factory =
                    globalContext.findPart(extension.get(CONFIG_KEY_LAYER1_ENGINE).asString(),
                                           ObjectStoraceSpaceFactory.class);

            return factory.create(extension.getId(), extension);
        } catch (Exception exception) {
            Exceptions.handle()
                      .error(exception)
                      .to(StorageUtils.LOG)
                      .withSystemErrorMessage(
                              "Layer 1: Failed to create the object storage space '%s' of type '%s': %s (%s)",
                              extension.getId(),
                              extension.getString(CONFIG_KEY_LAYER1_ENGINE))
                      .handle();
            return null;
        }
    }

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
     * Returns all known layer 1 spaces.
     *
     * @return all known storage spaces of the layer 1
     */
    public Collection<ObjectStorageSpace> getSpaces() {
        return getSpaceMap().values();
    }

    /**
     * Returns the storage space with the given name.
     *
     * @param name the name of the space
     * @return a wrapper which is used to access the objects stored in the storage space
     * @throws sirius.kernel.health.HandledException if an unknown storage space is requested.
     */
    public ObjectStorageSpace getSpace(String name) {
        ObjectStorageSpace result = getSpaceMap().get(name);
        if (result == null) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .withSystemErrorMessage("Layer1: Cannot find a configuration for space '%s'"
                                                    + ". Please verify the system configuration.", name)
                            .handle();
        }

        return result;
    }
}
