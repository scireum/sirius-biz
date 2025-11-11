/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1.replication;

import sirius.biz.storage.layer1.ObjectStorage;
import sirius.biz.storage.layer1.ObjectStorageSpace;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Average;

import javax.annotation.Nullable;
import java.io.InputStream;
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
    @Nullable
    private ReplicationTaskStorage taskStorage;

    private final Average replicationExecutionDuration = new Average();

    /**
     * Initializes the replication relations on the given set (map) of spaces.
     *
     * @param spaces the spaces to check and link properly
     */
    public void initializeReplication(Map<String, ObjectStorageSpace> spaces) {
        Map<String, String> reverseLookup = new HashMap<>();
        spaces.forEach((name, space) -> {
            String replicationSpaceName = space.getSettings().getString(CONFIG_KEY_LAYER1_REPLICATION_SPACE);
            if (Strings.isFilled(replicationSpaceName)) {
                space.withReplicationSpace(resolveReplicationSpace(name, replicationSpaceName, spaces, reverseLookup));
            }
        });
    }

    private ObjectStorageSpace resolveReplicationSpace(String primarySpace,
                                                       String replicationSpace,
                                                       Map<String, ObjectStorageSpace> spaces,
                                                       Map<String, String> reverseLookup) {
        ObjectStorageSpace result = spaces.get(replicationSpace);

        if (result == null) {
            StorageUtils.LOG.WARN("Layer 1: Cannot use unknown space '%s' as replication space for '%s'!",
                                  replicationSpace,
                                  primarySpace);
            return null;
        }
        if (reverseLookup.containsKey(replicationSpace)) {
            StorageUtils.LOG.WARN(
                    "Layer 1: Cannot use space '%s' as replication space for '%s' as it is already the replication space for '%s'!",
                    replicationSpace,
                    primarySpace,
                    reverseLookup.get(replicationSpace));
            return null;
        }
        if (hasCycle(primarySpace, result)) {
            StorageUtils.LOG.WARN(
                    "Layer 1: Cannot use space '%s' as replication space for '%s' as this would create a cycle!",
                    replicationSpace,
                    primarySpace,
                    reverseLookup.get(replicationSpace));
            return null;
        }

        reverseLookup.put(replicationSpace, primarySpace);

        return result;
    }

    private boolean hasCycle(String primarySpace, ObjectStorageSpace replicationSpace) {
        ObjectStorageSpace spaceToCheck = replicationSpace;
        while (spaceToCheck != null) {
            if (Strings.areEqual(primarySpace, spaceToCheck.getName())) {
                return true;
            }

            spaceToCheck = spaceToCheck.getReplicationSpace();
        }

        return false;
    }

    /**
     * Notifies the replication system about the deletion of an object.
     *
     * @param primarySpace the space of the object being deleted
     * @param objectId     the id of the object being deleted
     */
    public void notifyAboutDelete(ObjectStorageSpace primarySpace, String objectId) {
        if (taskStorage != null && primarySpace.hasReplicationSpace()) {
            taskStorage.notifyAboutDelete(primarySpace.getName(), objectId);
        }
    }

    /**
     * Notifies the replication system about the modification of an object.
     *
     * @param primarySpace  the space of the object being modified
     * @param objectId      the id of the object being modified
     * @param contentLength the expected content length
     */
    public void notifyAboutUpdate(ObjectStorageSpace primarySpace, String objectId, long contentLength) {
        if (taskStorage != null && primarySpace.hasReplicationSpace()) {
            taskStorage.notifyAboutUpdate(primarySpace.getName(), objectId, contentLength);
        }
    }

    /**
     * Actually execute a replication task.
     * <p>
     * Note that this API should be used by the {@link ReplicationTaskStorage} to actually execute a replication task.
     *
     * @param space         the primary space of the object to replicate
     * @param objectId      the id of the object to replicate
     * @param contentLength the expected content length to transfer
     * @param performDelete <tt>true</tt> to replicate a deletion, <tt>false</tt> to replicate a modification
     * @throws Exception in case of an error when replicating the changes performed on the specified object
     */
    public void executeReplicationTask(String space, String objectId, long contentLength, boolean performDelete)
            throws Exception {
        if (taskStorage == null) {
            throw new IllegalStateException("Cannot execute replication tasks without a storage!");
        }

        ObjectStorageSpace primarySpace = objectStorage.getSpace(space);
        if (!primarySpace.hasReplicationSpace()) {
            return;
        }

        Watch watch = Watch.start();
        if (performDelete) {
            primarySpace.getReplicationSpace().delete(objectId);
        } else {
            try (InputStream in = primarySpace.getInputStream(objectId)
                                              .orElseThrow(() -> new IllegalStateException("No InputStream is available"))) {
                primarySpace.getReplicationSpace().upload(objectId, in, contentLength);
            }
        }
        replicationExecutionDuration.addValue(watch.elapsedMillis());
    }

    /**
     * Provides access to the underlying replication task storage (if available).
     *
     * @return the metadata store for the replication tasks or an empty optional if no store is available
     */
    public Optional<ReplicationTaskStorage> getReplicationTaskStorage() {
        return Optional.ofNullable(taskStorage);
    }

    /**
     * Exposes the metric which records the replication tasks performed on this node.
     * <p>
     * This is mainly exposed by the used by {@link sirius.biz.storage.util.StorageMetrics}.
     *
     * @return the average which records the duration of the replication tasks executed by this node
     */
    public Average getReplicationExecutionDuration() {
        return replicationExecutionDuration;
    }
}
