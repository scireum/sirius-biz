/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1.replication;

import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer1.ObjectStorage;
import sirius.biz.storage.layer1.ObjectStorageSpace;
import sirius.biz.storage.util.DerivedSpaceInfo;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.settings.Extension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Manages the replication of layer 1 storage spaces.
 * <p>
 * Note that internally the metadata required to ensure the proper synchronisation can either be stored in a
 * JDBC database or a MongoDB.
 * <p>
 * Therefore either <tt>biz-storage-replication-jdbc</tt> or <tt>biz-storage-replication-mongo</tt> has to be enabled
 * as framework if replication is used.
 */
@Register(classes = ReplicationManager.class, framework = StorageUtils.FRAMEWORK_STORAGE)
public class ReplicationManager {

    /**
     * Contains the name of the replication space.
     */
    public static final String CONFIG_KEY_LAYER1_REPLICATION_SPACE = "replicationSpace";

    @Part
    private ObjectStorage objectStorage;

    @Part
    private ReplicationTaskStorage taskStorage;

    private DerivedSpaceInfo<String> replicationSpaces = new DerivedSpaceInfo<>(CONFIG_KEY_LAYER1_REPLICATION_SPACE,
                                                                                StorageUtils.ConfigScope.LAYER1,
                                                                                this::resolveReplicationSpace);

    private Map<String, String> reverseLookup = new HashMap<>();

    private String resolveReplicationSpace(Extension ext) {
        String replicationSpace = ext.getString(CONFIG_KEY_LAYER1_REPLICATION_SPACE);
        if (Strings.isEmpty(replicationSpace)) {
            return "";
        }

        if (Strings.areEqual(ext.getId(), replicationSpace)) {
            StorageUtils.LOG.WARN("Layer 1: A storage space cannot replicate onto itself ('%s')!", replicationSpace);

            return "";
        }

        if (!objectStorage.isKnown(replicationSpace)) {
            StorageUtils.LOG.WARN("Layer 1: Cannot use unknown space '%s' as replication space for '%s'!",
                                  replicationSpace,
                                  ext.getId());

            return "";
        }

        if (reverseLookup.containsKey(replicationSpace)) {
            StorageUtils.LOG.WARN(
                    "Layer 1: Cannot use space '%s' as replication space for '%s' as it is already the replication space for '%s'!",
                    replicationSpace,
                    ext.getId(),
                    reverseLookup.get(replicationSpace));

            return "";
        }

        reverseLookup.put(replicationSpace, ext.getId());

        return replicationSpace;
    }

    /**
     * Determines if a replication space is available for the given one.
     *
     * @param primarySpace the space to check
     * @return <tt>true</tt> if a replication space is available, <tt>false</tt> otherwise
     */
    public boolean hasReplicationSpace(String primarySpace) {
        return Strings.isFilled(replicationSpaces.get(primarySpace));
    }

    /**
     * Returns the replication space of the given primary space.
     *
     * @param primarySpace the space to determine the replication space for
     * @return the replication space wrapped as optional or an empty one if there is no replication configured
     */
    public Optional<ObjectStorageSpace> getReplicationSpace(String primarySpace) {
        String replicationSpace = replicationSpaces.get(primarySpace);
        if (Strings.isEmpty(replicationSpace)) {
            return Optional.empty();
        }

        return Optional.of(objectStorage.getSpace(replicationSpace));
    }

    /**
     * Notifies the replication system about the deletion of an object.
     *
     * @param primarySpace the space of the object being deleted
     * @param objectId     the id of the object being deleted
     */
    public void notifyAboutDelete(String primarySpace, String objectId) {
        if (taskStorage != null && hasReplicationSpace(primarySpace)) {
            taskStorage.notifyAboutDelete(primarySpace, objectId);
        }
    }

    /**
     * Notifies the replication system about the modification of an object.
     *
     * @param primarySpace the space of the object being modified
     * @param objectId     the id of the object being modified
     */
    public void notifyAboutUpdate(String primarySpace, String objectId) {
        if (taskStorage != null && hasReplicationSpace(primarySpace)) {
            taskStorage.notifyAboutUpdate(primarySpace, objectId);
        }
    }

    /**
     * Actually execute a replication task.
     * <p>
     * Note that this API should be used by the {@link ReplicationTaskStorage} to actually execute a replication task.
     *
     * @param space         the primary space of the object to replicate
     * @param objectId      the id of the object to replicate
     * @param performDelete <tt>true</tt> to replicate a delete, <tt>false</tt> to replicate a modification
     * @throws Exception in case of an error when replicating the changes performed on the specified object
     */
    public void executeReplicationTask(String space, String objectId, boolean performDelete) throws Exception {
        if (taskStorage == null) {
            throw new IllegalStateException("Cannot execute replication tasks without a storage!");
        }

        ObjectStorageSpace replicationSpace = getReplicationSpace(space).orElse(null);
        if (replicationSpace == null) {
            return;
        }

        if (performDelete) {
            replicationSpace.delete(objectId);
        } else {
            ObjectStorageSpace primarySpace = objectStorage.getSpace(space);
            try (FileHandle handle = primarySpace.download(objectId).orElse(null)) {
                if (handle != null) {
                    replicationSpace.upload(objectId, handle.getFile());
                }
            }
        }
    }

    /**
     * Provides access to the underlying replication task storage (if available).
     *
     * @return the metadata store for the replication tasks or an empty optional if no store is available
     */
    public Optional<ReplicationTaskStorage> getReplicationTaskStorage() {
        return Optional.ofNullable(taskStorage);
    }
}
