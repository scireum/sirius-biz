/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2.mongo;

import sirius.biz.storage.layer2.BasicBlobStorageSpace;
import sirius.biz.storage.layer2.Blob;
import sirius.biz.storage.layer2.Directory;
import sirius.biz.storage.layer2.variants.BlobVariant;
import sirius.biz.storage.layer2.variants.ConversionProcess;
import sirius.biz.storage.util.StorageUtils;
import sirius.biz.web.BasePageHelper;
import sirius.biz.web.MongoPageHelper;
import sirius.db.mixing.DateRange;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.Mixing;
import sirius.db.mixing.query.QueryField;
import sirius.db.mongo.Mango;
import sirius.db.mongo.Mongo;
import sirius.db.mongo.MongoQuery;
import sirius.db.mongo.QueryBuilder;
import sirius.db.mongo.Updater;
import sirius.kernel.async.CallContext;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.settings.Extension;
import sirius.web.http.WebContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Provides a storage facility which stores blobs and directories as {@link MongoBlob} and {@link MongoDirectory} in a
 * MongoDB.
 */
public class MongoBlobStorageSpace extends BasicBlobStorageSpace<MongoBlob, MongoDirectory, MongoVariant> {

    /**
     * Determines the number of attempts when updating the contents of a blob.
     */
    private static final int UPDATE_BLOB_RETRIES = 3;

    @Part
    private static Mango mango;

    @Part
    private static Mongo mongo;

    @Part
    private static Mixing mixing;

    @Part
    private static StorageUtils storageUtils;

    protected MongoBlobStorageSpace(String spaceName, Extension config) {
        super(spaceName, config);
    }

    @Override
    public Optional<? extends Blob> findByBlobKey(String key) {
        if (Strings.isEmpty(key)) {
            return Optional.empty();
        }

        return mango.select(MongoBlob.class)
                    .eq(MongoBlob.SPACE_NAME, spaceName)
                    .eq(MongoBlob.BLOB_KEY, key)
                    .eq(MongoBlob.DELETED, false)
                    .eq(MongoBlob.COMMITTED, true)
                    .first();
    }

    @Override
    protected MongoDirectory findRoot(String tenantId) {
        return mango.select(MongoDirectory.class)
                    .eq(MongoDirectory.SPACE_NAME, spaceName)
                    .eq(MongoDirectory.TENANT_ID, tenantId)
                    .eq(MongoDirectory.DELETED, false)
                    .eq(MongoDirectory.PARENT, null)
                    .queryFirst();
    }

    @Override
    protected boolean isSingularRoot(MongoDirectory directory) {
        return mango.select(MongoDirectory.class)
                    .eq(MongoDirectory.SPACE_NAME, directory.getSpaceName())
                    .eq(MongoDirectory.TENANT_ID, directory.getTenantId())
                    .eq(MongoDirectory.DELETED, false)
                    .eq(MongoDirectory.PARENT, null)
                    .count() == 1;
    }

    @Override
    protected MongoDirectory createRoot(String tenantId) {
        MongoDirectory directory = new MongoDirectory();
        directory.setSpaceName(spaceName);
        directory.setTenantId(tenantId);
        mango.update(directory);
        return directory;
    }

    @Override
    protected void commitDirectory(MongoDirectory directory) {
        directory.setCommitted(true);

        // Use a direct update as there is no need to trigger a change log or to update the last
        // modified timestamps.
        mongo.update()
             .set(MongoDirectory.COMMITTED, true)
             .where(MongoDirectory.ID, directory.getId())
             .executeForOne(MongoDirectory.class);
    }

    @Override
    protected void rollbackDirectory(MongoDirectory directory) {
        // This is an uncommitted directory - no need to trigger any delete handlers...
        mongo.delete().where(MongoDirectory.ID, directory.getId()).singleFrom(MongoDirectory.class);
    }

    @Override
    protected MongoDirectory lookupDirectoryById(String idAsString) {
        return mango.find(MongoDirectory.class, idAsString).orElse(null);
    }

    @Override
    public Blob createTemporaryBlob() {
        MongoBlob result = new MongoBlob();
        result.setSpaceName(spaceName);
        result.setTemporary(true);
        result.setCommitted(true);
        mango.update(result);

        return result;
    }

    @Override
    public Blob createTemporaryBlob(String tenantId) {
        MongoBlob result = new MongoBlob();
        result.setSpaceName(spaceName);
        result.setTenantId(tenantId);
        result.setTemporary(true);
        result.setCommitted(true);
        mango.update(result);

        return result;
    }

    @Override
    public Optional<? extends Blob> findAttachedBlobByName(String referencingEntity, String filename) {
        if (Strings.isEmpty(referencingEntity)) {
            return Optional.empty();
        }

        return mango.select(MongoBlob.class)
                    .eq(MongoBlob.SPACE_NAME, spaceName)
                    .eq(effectiveFilenameMapping(), effectiveFilename(filename))
                    .eq(MongoBlob.REFERENCE, referencingEntity)
                    .eq(MongoBlob.REFERENCE_DESIGNATOR, null)
                    .eq(MongoBlob.DELETED, false)
                    .eq(MongoBlob.COMMITTED, true)
                    .first();
    }

    private String effectiveFilename(String filename) {
        if (useNormalizedNames && filename != null) {
            return storageUtils.sanitizePath(filename).toLowerCase();
        }

        return storageUtils.sanitizePath(filename);
    }

    private Mapping effectiveFilenameMapping() {
        return useNormalizedNames ? MongoBlob.NORMALIZED_FILENAME : MongoBlob.FILENAME;
    }

    @Override
    public List<? extends Blob> findAttachedBlobs(String referencingEntity) {
        if (Strings.isEmpty(referencingEntity)) {
            return Collections.emptyList();
        }

        return mango.select(MongoBlob.class)
                    .eq(MongoBlob.SPACE_NAME, spaceName)
                    .eq(MongoBlob.REFERENCE, referencingEntity)
                    .eq(MongoBlob.REFERENCE_DESIGNATOR, null)
                    .eq(MongoBlob.COMMITTED, true)
                    .eq(MongoBlob.DELETED, false)
                    .orderAsc(MongoBlob.FILENAME)
                    .queryList();
    }

    @Override
    public void deleteAttachedBlobs(String referencingEntity) {
        if (Strings.isEmpty(referencingEntity)) {
            return;
        }

        mongo.update()
             .set(MongoBlob.DELETED, true)
             .where(MongoBlob.SPACE_NAME, spaceName)
             .where(MongoBlob.REFERENCE, referencingEntity)
             .where(MongoBlob.REFERENCE_DESIGNATOR, null)
             .executeForMany(MongoBlob.class);
    }

    @Override
    public void deleteReferencedBlobs(String referencingEntity,
                                      String referenceDesignator,
                                      @Nullable String excludedBlobKey) {
        if (Strings.isEmpty(referencingEntity)) {
            return;
        }

        if (Strings.isEmpty(referenceDesignator)) {
            return;
        }

        Updater updater = mongo.update()
                               .set(MongoBlob.DELETED, true)
                               .where(MongoBlob.SPACE_NAME, spaceName)
                               .where(MongoBlob.REFERENCE, referencingEntity)
                               .where(MongoBlob.REFERENCE_DESIGNATOR, referenceDesignator);

        if (Strings.isFilled(excludedBlobKey)) {
            updater.where(QueryBuilder.FILTERS.ne(MongoBlob.BLOB_KEY, excludedBlobKey));
        }

        updater.executeForMany(MongoBlob.class);
    }

    @Override
    public void attachBlobByType(String objectKey, String referencingEntity, String referenceDesignator) {
        if (Strings.isEmpty(referencingEntity)) {
            return;
        }

        if (Strings.isEmpty(referenceDesignator)) {
            return;
        }

        if (Strings.isEmpty(objectKey)) {
            return;
        }

        // Remove any previous references...
        mongo.update()
             .set(MongoBlob.REFERENCE, null)
             .set(MongoBlob.REFERENCE_DESIGNATOR, null)
             .where(MongoBlob.SPACE_NAME, spaceName)
             .where(MongoBlob.REFERENCE, referencingEntity)
             .where(MongoBlob.REFERENCE_DESIGNATOR, referenceDesignator)
             .where(MongoBlob.TEMPORARY, false)
             .where(MongoBlob.COMMITTED, true)
             .where(MongoBlob.DELETED, false)
             .executeForMany(MongoBlob.class);

        // Place new reference...
        long numChanges = mongo.update()
                               .set(MongoBlob.REFERENCE, referencingEntity)
                               .set(MongoBlob.REFERENCE_DESIGNATOR, referenceDesignator)
                               .where(MongoBlob.SPACE_NAME, spaceName)
                               .where(MongoBlob.BLOB_KEY, objectKey)
                               .where(MongoBlob.REFERENCE, null)
                               .where(MongoBlob.REFERENCE_DESIGNATOR, null)
                               .where(MongoBlob.TEMPORARY, false)
                               .where(MongoBlob.COMMITTED, true)
                               .where(MongoBlob.DELETED, false)
                               .executeForOne(MongoBlob.class)
                               .getModifiedCount();
        if (numChanges == 0) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .withSystemErrorMessage(
                                    "Layer 2/Mongo: An error occurred, cannot reference '%s' from '%s' ('%s'): The blob is either deleted, temporary or already in use.",
                                    objectKey,
                                    referencingEntity,
                                    referenceDesignator)
                            .handle();
        }
    }

    @Override
    public Optional<? extends Blob> findAttachedBlobByKey(String referencingEntity, String blobKey) {
        if (Strings.isEmpty(referencingEntity)) {
            return Optional.empty();
        }

        return mango.select(MongoBlob.class)
                    .eq(MongoBlob.SPACE_NAME, spaceName)
                    .eq(MongoBlob.BLOB_KEY, blobKey)
                    .eq(MongoBlob.REFERENCE, referencingEntity)
                    .eq(MongoBlob.REFERENCE_DESIGNATOR, null)
                    .eq(MongoBlob.DELETED, false)
                    .eq(MongoBlob.COMMITTED, true)
                    .first();
    }

    @Override
    public void attachTemporaryBlob(String objectKey, String referencingEntity) {
        if (Strings.isEmpty(referencingEntity)) {
            return;
        }

        if (Strings.isEmpty(objectKey)) {
            return;
        }

        long numChanges = mongo.update()
                               .set(MongoBlob.REFERENCE, referencingEntity)
                               .set(MongoBlob.TEMPORARY, false)
                               .where(MongoBlob.SPACE_NAME, spaceName)
                               .where(MongoBlob.BLOB_KEY, objectKey)
                               .where(MongoBlob.REFERENCE, null)
                               .where(MongoBlob.REFERENCE_DESIGNATOR, null)
                               .where(MongoBlob.TEMPORARY, true)
                               .where(MongoBlob.COMMITTED, true)
                               .where(MongoBlob.DELETED, false)
                               .executeForOne(MongoBlob.class)
                               .getModifiedCount();
        if (numChanges == 0) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .withSystemErrorMessage(
                                    "Layer 2/Mongo: An error occurred, cannot reference '%s' from '%s': The blob is either deleted, not temporary or already in use.",
                                    objectKey,
                                    referencingEntity)
                            .handle();
        }
    }

    @Override
    public Optional<? extends Blob> findAttachedBlobByType(String referencingEntity, String referenceDesignator) {
        return mango.select(MongoBlob.class)
                    .eq(MongoBlob.SPACE_NAME, spaceName)
                    .eq(MongoBlob.REFERENCE, referencingEntity)
                    .eq(MongoBlob.REFERENCE_DESIGNATOR, referenceDesignator)
                    .eq(MongoBlob.COMMITTED, true)
                    .eq(MongoBlob.DELETED, false)
                    .first();
    }

    @Override
    public void markAsUsed(String referencingEntity, String referenceDesignator, String objectKey) {
        if (Strings.isEmpty(referencingEntity)) {
            return;
        }

        if (Strings.isEmpty(referenceDesignator)) {
            return;
        }

        if (Strings.isEmpty(objectKey)) {
            return;
        }

        mongo.update()
             .set(MongoBlob.REFERENCE, referencingEntity)
             .set(MongoBlob.REFERENCE_DESIGNATOR, referenceDesignator)
             .set(MongoBlob.TEMPORARY, false)
             .where(MongoBlob.SPACE_NAME, spaceName)
             .where(MongoBlob.BLOB_KEY, objectKey)
             .where(MongoBlob.TEMPORARY, true)
             .executeForOne(MongoBlob.class);
    }

    @Override
    public void markAsUsed(Blob blob) {
        if (blob == null) {
            return;
        }

        mongo.update()
             .set(MongoBlob.TEMPORARY, false)
             .where(MongoBlob.SPACE_NAME, spaceName)
             .where(MongoBlob.ID, ((MongoBlob) blob).getId())
             .where(MongoBlob.TEMPORARY, true)
             .executeForOne(MongoBlob.class);
    }

    @Override
    protected void markBlobAsDeleted(MongoBlob blob) {
        mongo.update().set(MongoBlob.DELETED, true).where(MongoBlob.ID, blob.getId()).executeForOne(MongoBlob.class);
    }

    @Override
    protected void updateBlobName(MongoBlob blob, String newName) {
        blob.setFilename(newName);
        // Trigger a conventional update to ensure that last modified get updated and a changelog is triggered
        mango.update(blob);
    }

    @Override
    public void updateBlobReadOnlyFlag(MongoBlob blob, boolean readOnly) {
        mongo.update()
             .set(MongoBlob.READ_ONLY, readOnly)
             .where(MongoBlob.ID, blob.getId())
             .executeForOne(MongoBlob.class);
    }

    @Override
    protected void updateBlobParent(MongoBlob blob, MongoDirectory newParent) {
        blob.getParentRef().setValue(newParent);
        // Trigger a conventional update to ensure that last modified get updated and a changelog is triggered
        mango.update(blob);
    }

    @Override
    protected void markDirectoryAsDeleted(MongoDirectory directory) {
        mongo.update()
             .set(MongoDirectory.DELETED, true)
             .where(MongoDirectory.ID, directory.getId())
             .executeForOne(MongoDirectory.class);
    }

    @Override
    protected void updateDirectoryParent(MongoDirectory directory, MongoDirectory newParent) {
        directory.getParentRef().setValue(newParent);
        // Trigger a conventional update to ensure that last modified get updated and a changelog is triggered
        mango.update(directory);
    }

    @Override
    protected void updateDirectoryName(MongoDirectory directory, String newName) {
        directory.setDirectoryName(newName);
        // Trigger a conventional update to ensure that last modified get updated and a changelog is triggered
        mango.update(directory);
    }

    protected MongoDirectory fetchDirectoryParent(MongoDirectory directory) {
        return (MongoDirectory) fetchDirectoryById(directory.getParentRef().getIdAsString());
    }

    protected MongoDirectory fetchBlobParent(MongoBlob blob) {
        return (MongoDirectory) fetchDirectoryById(blob.getParentRef().getIdAsString());
    }

    @Nonnull
    @Override
    @SuppressWarnings("java:S2259")
    @Explain("String filled check is performed on filename.")
    protected Optional<String> updateBlob(@Nonnull MongoBlob blob,
                                          @Nonnull String nextPhysicalId,
                                          long size,
                                          @Nullable String filename,
                                          @Nullable String checksum) throws Exception {
        int retries = UPDATE_BLOB_RETRIES;
        while (retries-- > 0) {
            Updater updater = mongo.update()
                                   .set(MongoBlob.PHYSICAL_OBJECT_KEY, nextPhysicalId)
                                   .set(MongoBlob.SIZE, size)
                                   .set(MongoBlob.LAST_MODIFIED, LocalDateTime.now());
            if (Strings.isFilled(filename)) {
                filename = filename.trim();
                updater.set(MongoBlob.FILENAME, filename)
                       .set(MongoBlob.NORMALIZED_FILENAME, filename.toLowerCase())
                       .set(MongoBlob.FILE_EXTENSION, Files.getFileExtension(filename.toLowerCase()));
            }
            if (Strings.isFilled(checksum)) {
                updater.set(MongoBlob.CHECKSUM, checksum);
            } else {
                updater.unset(MongoBlob.CHECKSUM);
            }

            String previousPhysicalObjectKey = blob.getPhysicalObjectKey();
            if (Strings.isFilled(previousPhysicalObjectKey)) {
                updater.set(MongoBlob.CONTENT_UPDATED, true);
            } else {
                updater.set(MongoBlob.CREATED, true);
            }

            long numUpdated = updater.where(MongoBlob.ID, blob.getId())
                                     .where(MongoBlob.PHYSICAL_OBJECT_KEY, blob.getPhysicalObjectKey())
                                     .executeForOne(MongoBlob.class)
                                     .getModifiedCount();
            if (numUpdated == 1) {
                // Also update in-memory to avoid an additional database fetch...
                blob.setPhysicalObjectKey(nextPhysicalId);
                if (Strings.isFilled(filename)) {
                    blob.setFilename(filename);
                }
                blob.updateFilenameFields();
                blob.setSize(size);
                blob.setLastModified(LocalDateTime.now());

                return Optional.ofNullable(previousPhysicalObjectKey);
            } else if (retries > 0) {
                blob = mango.refreshOrFail(blob);
            }
        }

        throw new IllegalStateException(Strings.apply("Cannot update the contents after %s retries.",
                                                      UPDATE_BLOB_RETRIES));
    }

    protected boolean hasExistingChild(MongoDirectory parent,
                                       String childName,
                                       Directory exemptedDirectory,
                                       Blob exemptedBlob) {
        return hasExistingChildDirectory(parent, childName, exemptedDirectory) || hasExistingChildBlob(parent,
                                                                                                       childName,
                                                                                                       exemptedBlob);
    }

    private MongoQuery<MongoDirectory> childDirectoryQuery(MongoDirectory parent, String childName) {
        return mango.select(MongoDirectory.class)
                    .eq(MongoDirectory.SPACE_NAME, spaceName)
                    .eq(MongoDirectory.PARENT, parent)
                    .eq(effectiveDirectoryNameMapping(), effectiveFilename(childName))
                    .eq(MongoDirectory.COMMITTED, true)
                    .eq(MongoDirectory.DELETED, false);
    }

    private Mapping effectiveDirectoryNameMapping() {
        return useNormalizedNames ? MongoDirectory.NORMALIZED_DIRECTORY_NAME : MongoDirectory.DIRECTORY_NAME;
    }

    protected Optional<MongoDirectory> findExistingChildDirectory(MongoDirectory parent, String childName) {
        return childDirectoryQuery(parent, childName).first();
    }

    @Override
    protected MongoDirectory findAnyChildDirectory(MongoDirectory parent, String childName) {
        return mango.select(MongoDirectory.class)
                    .eq(MongoDirectory.SPACE_NAME, spaceName)
                    .eq(MongoDirectory.PARENT, parent)
                    .eq(effectiveDirectoryNameMapping(), effectiveFilename(childName))
                    .eq(MongoDirectory.DELETED, false)
                    .queryFirst();
    }

    @Override
    protected MongoDirectory createChildDirectory(MongoDirectory parent, String childName) {
        MongoDirectory newDirectory = new MongoDirectory();
        newDirectory.setSpaceName(spaceName);
        newDirectory.getParentRef().setValue(parent);
        newDirectory.setTenantId(parent.getTenantId());
        newDirectory.setDirectoryName(childName);
        newDirectory.setCommitted(false);
        mango.update(newDirectory);

        return newDirectory;
    }

    @Override
    protected boolean isChildDirectoryUnique(MongoDirectory parent, String childName, MongoDirectory childDirectory) {
        return mango.select(MongoDirectory.class)
                    .eq(MongoDirectory.SPACE_NAME, spaceName)
                    .eq(MongoDirectory.PARENT, parent)
                    .eq(effectiveDirectoryNameMapping(), effectiveFilename(childName))
                    .eq(MongoDirectory.DELETED, false)
                    .count() == 1;
    }

    protected void listChildDirectories(MongoDirectory parent,
                                        String prefixFilter,
                                        int maxResults,
                                        Predicate<? super Directory> childProcessor) {
        mango.select(MongoDirectory.class)
             .eq(MongoDirectory.SPACE_NAME, spaceName)
             .eq(MongoDirectory.PARENT, parent)
             .eq(MongoDirectory.COMMITTED, true)
             .eq(MongoDirectory.DELETED, false)
             .where(QueryBuilder.FILTERS.prefix(MongoDirectory.NORMALIZED_DIRECTORY_NAME, prefixFilter))
             .limit(maxResults)
             .orderAsc(MongoDirectory.NORMALIZED_DIRECTORY_NAME)
             .iterate(childProcessor::test);
    }

    protected Optional<MongoBlob> findExistingChildBlob(MongoDirectory parent, String childName) {
        return childBlobQuery(parent, childName).first();
    }

    private MongoQuery<MongoBlob> childBlobQuery(MongoDirectory parent, String childName) {
        return mango.select(MongoBlob.class)
                    .eq(MongoBlob.SPACE_NAME, spaceName)
                    .eq(MongoBlob.PARENT, parent)
                    .eq(effectiveFilenameMapping(), effectiveFilename(childName))
                    .eq(MongoBlob.COMMITTED, true)
                    .eq(MongoBlob.DELETED, false);
    }

    @Override
    protected MongoBlob findAnyChildBlob(MongoDirectory parent, String childName) {
        return mango.select(MongoBlob.class)
                    .eq(MongoBlob.SPACE_NAME, spaceName)
                    .eq(MongoBlob.PARENT, parent)
                    .eq(effectiveFilenameMapping(), effectiveFilename(childName))
                    .eq(MongoBlob.DELETED, false)
                    .queryFirst();
    }

    @Override
    protected MongoBlob createChildBlob(MongoDirectory parent, String childName) {
        MongoBlob newBlob = new MongoBlob();
        newBlob.setSpaceName(spaceName);
        newBlob.getParentRef().setValue(parent);
        newBlob.setTenantId(parent.getTenantId());
        newBlob.setFilename(childName);
        newBlob.setCommitted(false);
        mango.update(newBlob);

        return newBlob;
    }

    @Override
    protected boolean isChildBlobUnique(MongoDirectory parent, String childName, MongoBlob childBlob) {
        return mango.select(MongoBlob.class)
                    .eq(MongoBlob.SPACE_NAME, spaceName)
                    .eq(MongoBlob.PARENT, parent)
                    .eq(effectiveFilenameMapping(), effectiveFilename(childName))
                    .eq(MongoBlob.DELETED, false)
                    .count() == 1;
    }

    @Override
    protected void commitBlob(MongoBlob blob) {
        blob.setCommitted(true);

        // Use a direct update as there is no need to trigger a change log or to update the last
        // modified timestamps.
        mongo.update().set(MongoBlob.COMMITTED, true).where(MongoBlob.ID, blob.getId()).executeForOne(MongoBlob.class);
    }

    @Override
    protected void rollbackBlob(MongoBlob blob) {
        // This is an uncommitted blob - no need to trigger any delete handlers...
        mongo.delete().where(MongoBlob.ID, blob.getId()).singleFrom(MongoBlob.class);
    }

    @Override
    protected MongoBlob findAnyAttachedBlobByName(String referencingEntity, String filename) {
        return mango.select(MongoBlob.class)
                    .eq(MongoBlob.SPACE_NAME, spaceName)
                    .eq(effectiveFilenameMapping(), effectiveFilename(filename))
                    .eq(MongoBlob.REFERENCE, referencingEntity)
                    .eq(MongoBlob.REFERENCE_DESIGNATOR, null)
                    .eq(MongoBlob.DELETED, false)
                    .queryFirst();
    }

    @Override
    protected MongoBlob createAttachedBlobByName(String referencingEntity, String filename) {
        MongoBlob result = new MongoBlob();
        result.setSpaceName(spaceName);
        result.setFilename(filename);
        result.setReference(referencingEntity);
        result.setCommitted(false);
        mango.update(result);

        return result;
    }

    @Override
    protected boolean isAttachedBlobUnique(String referencingEntity, String filename, MongoBlob blob) {
        return mango.select(MongoBlob.class)
                    .eq(MongoBlob.SPACE_NAME, spaceName)
                    .eq(effectiveFilenameMapping(), effectiveFilename(filename))
                    .eq(MongoBlob.REFERENCE, referencingEntity)
                    .eq(MongoBlob.REFERENCE_DESIGNATOR, null)
                    .eq(MongoBlob.DELETED, false)
                    .count() == 1;
    }

    protected void listChildBlobs(MongoDirectory parent,
                                  String prefixFilter,
                                  Set<String> fileTypes,
                                  int maxResults,
                                  Predicate<? super Blob> childProcessor) {
        MongoQuery<MongoBlob> blobsQuery = mango.select(MongoBlob.class)
                                                .eq(MongoBlob.SPACE_NAME, spaceName)
                                                .eq(MongoBlob.PARENT, parent)
                                                .eq(MongoBlob.COMMITTED, true)
                                                .eq(MongoBlob.DELETED, false);

        if (fileTypes != null && !fileTypes.isEmpty()) {
            blobsQuery.where(QueryBuilder.FILTERS.containsOne(MongoBlob.FILE_EXTENSION, fileTypes.toArray()).build());
        }
        blobsQuery.where(QueryBuilder.FILTERS.or(QueryBuilder.FILTERS.prefix(MongoBlob.NORMALIZED_FILENAME,
                                                                             prefixFilter),
                                                 QueryBuilder.FILTERS.prefix(MongoBlob.FILE_EXTENSION, prefixFilter)));
        blobsQuery.limit(maxResults);

        if (sortByLastModified) {
            blobsQuery.orderDesc(MongoBlob.LAST_MODIFIED);
        } else {
            blobsQuery.orderAsc(MongoBlob.NORMALIZED_FILENAME);
        }

        blobsQuery.iterate(childProcessor::test);
    }

    protected BasePageHelper<? extends Blob, ?, ?, ?> queryChildBlobsAsPage(MongoDirectory parent,
                                                                            WebContext webContext) {
        MongoQuery<MongoBlob> blobsQuery = mango.select(MongoBlob.class)
                                                .eq(MongoBlob.SPACE_NAME, spaceName)
                                                .eq(MongoBlob.PARENT, parent)
                                                .eq(MongoBlob.COMMITTED, true)
                                                .eq(MongoBlob.DELETED, false);

        MongoPageHelper<MongoBlob> pageHelper = MongoPageHelper.withQuery(blobsQuery)
                                                               .withContext(webContext)
                                                               .withSearchFields(QueryField.startsWith(MongoBlob.NORMALIZED_FILENAME),
                                                                                 QueryField.startsWith(MongoBlob.FILE_EXTENSION));

        pageHelper.addTermAggregation(MongoBlob.FILE_EXTENSION);
        pageHelper.addTimeAggregation(MongoBlob.LAST_MODIFIED,
                                      false,
                                      DateRange.LAST_FIFTEEN_MINUTES,
                                      DateRange.LAST_TWO_HOURS,
                                      DateRange.TODAY,
                                      DateRange.YESTERDAY,
                                      DateRange.THIS_WEEK,
                                      DateRange.LAST_WEEK,
                                      DateRange.THIS_MONTH,
                                      DateRange.LAST_MONTH,
                                      DateRange.THIS_YEAR,
                                      DateRange.LAST_YEAR);
        if (touchTracking) {
            pageHelper.addTimeAggregation(MongoBlob.LAST_TOUCHED,
                                          false,
                                          DateRange.LAST_FIFTEEN_MINUTES,
                                          DateRange.LAST_TWO_HOURS,
                                          DateRange.TODAY,
                                          DateRange.YESTERDAY,
                                          DateRange.THIS_WEEK,
                                          DateRange.LAST_WEEK,
                                          DateRange.THIS_MONTH,
                                          DateRange.LAST_MONTH,
                                          DateRange.THIS_YEAR,
                                          DateRange.LAST_YEAR);
        }

        if (sortByLastModified) {
            pageHelper.addSortFacet(Tuple.create("$BlobStorageSpace.sortByLastModified",
                                                 query -> query.orderDesc(MongoBlob.LAST_MODIFIED)),
                                    Tuple.create("$BlobStorageSpace.sortByFilename",
                                                 query -> query.orderAsc(MongoBlob.NORMALIZED_FILENAME)));
        } else {
            pageHelper.addSortFacet(Tuple.create("$BlobStorageSpace.sortByFilename",
                                                 query -> query.orderAsc(MongoBlob.NORMALIZED_FILENAME)),
                                    Tuple.create("$BlobStorageSpace.sortByLastModified",
                                                 query -> query.orderDesc(MongoBlob.LAST_MODIFIED)));
        }

        return pageHelper;
    }

    protected List<? extends BlobVariant> fetchVariants(MongoBlob blob) {
        return mango.select(MongoVariant.class)
                    .eq(MongoVariant.BLOB, blob)
                    .orderAsc(MongoVariant.VARIANT_NAME)
                    .queryList();
    }

    @Override
    protected MongoVariant findCompletedVariant(MongoBlob blob, String variantName) {
        return mango.select(MongoVariant.class)
                    .eq(MongoVariant.BLOB, blob)
                    .ne(MongoVariant.PHYSICAL_OBJECT_KEY, null)
                    .eq(MongoVariant.VARIANT_NAME, variantName)
                    .queryFirst();
    }

    @Override
    protected MongoVariant findAnyVariant(MongoBlob blob, String variantName) {
        return mango.select(MongoVariant.class)
                    .eq(MongoVariant.BLOB, blob)
                    .eq(MongoVariant.VARIANT_NAME, variantName)
                    .queryFirst();
    }

    @Override
    protected MongoVariant createVariant(MongoBlob blob, String variantName) {
        MongoVariant variant = new MongoVariant();
        variant.getBlob().setValue(blob);
        variant.setVariantName(variantName);
        variant.setQueuedForConversion(true);
        variant.setNode(CallContext.getNodeName());
        variant.setLastConversionAttempt(LocalDateTime.now());
        variant.setNumAttempts(1);
        mango.update(variant);
        return variant;
    }

    @Override
    protected MongoVariant createVariant(MongoBlob blob,
                                         String variantName,
                                         String physicalObjectKey,
                                         long size,
                                         @Nullable String checksum) {
        MongoVariant variant = new MongoVariant();
        variant.getBlob().setValue(blob);
        variant.setVariantName(variantName);
        variant.setQueuedForConversion(false);
        variant.setNumAttempts(0);
        variant.setConversionDuration(0);
        variant.setTransferDuration(0);
        variant.setPhysicalObjectKey(physicalObjectKey);
        variant.setSize(size);
        variant.setNode(CallContext.getNodeName());
        variant.setLastConversionAttempt(LocalDateTime.now());
        variant.setNumAttempts(1);
        variant.setChecksum(checksum);
        mango.update(variant);
        return variant;
    }

    @Override
    protected boolean detectAndRemoveDuplicateVariant(MongoVariant variant, MongoBlob blob, String variantName) {
        if (mango.select(MongoVariant.class)
                 .ne(MongoVariant.ID, variant.getId())
                 .eq(MongoVariant.BLOB, blob)
                 .eq(MongoVariant.VARIANT_NAME, variantName)
                 .exists()) {
            // This is a new and unused variant, no need to trigger any delete handlers...
            mongo.delete().where(MongoVariant.ID, variant.getId()).singleFrom(MongoVariant.class);
            return true;
        }

        return false;
    }

    @Override
    protected boolean markConversionAttempt(MongoVariant variant) throws Exception {
        return mongo.update()
                    .set(MongoVariant.QUEUED_FOR_CONVERSION, true)
                    .set(MongoVariant.NODE, CallContext.getNodeName())
                    .set(MongoVariant.LAST_CONVERSION_ATTEMPT, LocalDateTime.now())
                    .inc(MongoVariant.NUM_ATTEMPTS, 1)
                    .where(MongoVariant.ID, variant.getId())
                    .where(MongoVariant.NUM_ATTEMPTS, variant.getNumAttempts())
                    .executeForOne(MongoVariant.class)
                    .getModifiedCount() == 1;
    }

    @Override
    protected void markConversionFailure(MongoVariant variant, ConversionProcess conversionProcess) {
        mongo.update()
             .set(MongoVariant.QUEUED_FOR_CONVERSION, false)
             .set(MongoVariant.CONVERSION_DURATION, conversionProcess.getConversionDuration())
             .set(MongoVariant.QUEUE_DURATION, conversionProcess.getQueueDuration())
             .set(MongoVariant.TRANSFER_DURATION, conversionProcess.getTransferDuration())
             .where(MongoVariant.ID, variant.getId())
             .executeForOne(MongoVariant.class);
    }

    @Override
    protected void markConversionSuccess(MongoVariant variant,
                                         String physicalKey,
                                         ConversionProcess conversionProcess) {
        Updater updater = mongo.update()
                               .set(MongoVariant.PHYSICAL_OBJECT_KEY, physicalKey)
                               .set(MongoVariant.SIZE, conversionProcess.getResultFileHandle().getFile().length())
                               .set(MongoVariant.QUEUED_FOR_CONVERSION, false)
                               .set(MongoVariant.CONVERSION_DURATION, conversionProcess.getConversionDuration())
                               .set(MongoVariant.QUEUE_DURATION, conversionProcess.getQueueDuration())
                               .set(MongoVariant.TRANSFER_DURATION, conversionProcess.getTransferDuration())
                               .where(MongoVariant.ID, variant.getId());

        String checksum = computeConversionCheckSum(conversionProcess);
        if (Strings.isFilled(checksum)) {
            updater.set(MongoVariant.CHECKSUM, checksum);
        } else {
            updater.unset(MongoVariant.CHECKSUM);
        }
        updater.executeForOne(MongoVariant.class);
    }

    @Override
    public void runCleanup() {
        deleteTemporaryBlobs();
        deleteOldBlobs();
    }

    private void deleteOldBlobs() {
        if (retentionDays <= 0) {
            return;
        }

        try {
            mango.select(MongoBlob.class)
                 .eq(MongoBlob.SPACE_NAME, spaceName)
                 .eq(MongoBlob.DELETED, false)
                 .where(QueryBuilder.FILTERS.lt(MongoBlob.LAST_MODIFIED, LocalDateTime.now().minusDays(retentionDays)))
                 .where(QueryBuilder.FILTERS.ltOrEmpty(MongoBlob.LAST_TOUCHED,
                                                       LocalDateTime.now().minusDays(retentionDays)))
                 .streamBlockwise()
                 .forEach(this::markBlobAsDeleted);
        } catch (Exception exception) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(exception)
                      .withSystemErrorMessage("Layer 2/Mongo: Failed to delete old blobs in %s: %s (%s)", spaceName)
                      .handle();
        }
    }

    protected void deleteTemporaryBlobs() {
        try {
            mango.select(MongoBlob.class)
                 .eq(MongoBlob.SPACE_NAME, spaceName)
                 .eq(MongoBlob.DELETED, false)
                 .eq(MongoBlob.TEMPORARY, true)
                 .where(QueryBuilder.FILTERS.lt(MongoBlob.LAST_MODIFIED, LocalDateTime.now().minusHours(4)))
                 .streamBlockwise()
                 .forEach(this::markBlobAsDeleted);
        } catch (Exception exception) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(exception)
                      .withSystemErrorMessage("Layer 2/Mongo: Failed to delete temporary blobs in %s: %s (%s)",
                                              spaceName)
                      .handle();
        }
    }

    @Override
    public void markTouched(Set<String> blobKeys) {
        blobKeys.forEach(blobKey -> mongo.update()
                                         .set(MongoBlob.LAST_TOUCHED, LocalDateTime.now())
                                         .where(MongoBlob.BLOB_KEY, blobKey)
                                         .executeForOne(MongoBlob.class));
    }

    @Override
    protected void purgeVariantFromCache(MongoBlob blob, String variantName) {
        blobKeyToPhysicalCache.remove(buildCacheLookupKey(blob.getBlobKey(), variantName));
    }

    private boolean hasExistingChildDirectory(MongoDirectory parent, String childName, Directory exemptedDirectory) {
        MongoQuery<MongoDirectory> childDirectoryQuery = childDirectoryQuery(parent, childName);
        if (exemptedDirectory != null) {
            childDirectoryQuery.ne(MongoDirectory.ID, ((MongoDirectory) exemptedDirectory).getId());
        }
        return childDirectoryQuery.exists();
    }

    private boolean hasExistingChildBlob(MongoDirectory parent, String childName, Blob exemptedBlob) {
        MongoQuery<MongoBlob> childBlobQuery = childBlobQuery(parent, childName);
        if (exemptedBlob != null) {
            childBlobQuery.ne(MongoBlob.BLOB_KEY, exemptedBlob.getBlobKey());
        }

        return childBlobQuery.exists();
    }
}
