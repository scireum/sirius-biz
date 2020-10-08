/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2.jdbc;

import sirius.biz.storage.layer2.BasicBlobStorageSpace;
import sirius.biz.storage.layer2.Blob;
import sirius.biz.storage.layer2.Directory;
import sirius.biz.storage.layer2.mongo.MongoBlob;
import sirius.biz.storage.layer2.variants.BlobVariant;
import sirius.biz.storage.util.StorageUtils;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.Operator;
import sirius.db.jdbc.SmartQuery;
import sirius.db.jdbc.UpdateStatement;
import sirius.db.jdbc.batch.BatchContext;
import sirius.db.jdbc.batch.UpdateQuery;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.Mixing;
import sirius.kernel.async.CallContext;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.settings.Extension;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Provides a storage facility which stores blobs and directories as {@link SQLBlob} and {@link SQLDirectory} in a
 * JDBC datasource.
 */
public class SQLBlobStorageSpace extends BasicBlobStorageSpace<SQLBlob, SQLDirectory, SQLVariant> {

    /**
     * Determines the number of attempts when updating the contents of a blob.
     */
    private static final int UPDATE_BLOB_RETRIES = 3;

    @Part
    private static OMA oma;

    @Part
    private static Mixing mixing;

    protected SQLBlobStorageSpace(String spaceName, Extension config) {
        super(spaceName, config);
    }

    @Override
    public Optional<? extends Blob> findByBlobKey(String key) {
        if (Strings.isEmpty(key)) {
            return Optional.empty();
        }

        return oma.select(SQLBlob.class)
                  .eq(SQLBlob.SPACE_NAME, spaceName)
                  .eq(SQLBlob.BLOB_KEY, key)
                  .eq(SQLBlob.DELETED, false)
                  .eq(SQLBlob.COMMITTED, true)
                  .first();
    }

    @Override
    protected SQLDirectory findRoot(String tenantId) {
        return oma.select(SQLDirectory.class)
                  .eq(SQLDirectory.SPACE_NAME, spaceName)
                  .eq(SQLDirectory.TENANT_ID, tenantId)
                  .eq(SQLDirectory.DELETED, false)
                  .eq(SQLDirectory.PARENT, null)
                  .queryFirst();
    }

    @Override
    protected boolean isSingularRoot(SQLDirectory directory) {
        return oma.select(SQLDirectory.class)
                  .eq(SQLDirectory.SPACE_NAME, directory.getSpaceName())
                  .eq(SQLDirectory.TENANT_ID, directory.getTenantId())
                  .eq(SQLDirectory.DELETED, false)
                  .eq(SQLDirectory.PARENT, null)
                  .count() == 1;
    }

    @Override
    protected SQLDirectory createRoot(String tenantId) {
        SQLDirectory directory = new SQLDirectory();
        directory.setSpaceName(spaceName);
        directory.setTenantId(tenantId);
        oma.update(directory);
        return directory;
    }

    @Override
    protected void commitDirectory(SQLDirectory directory) {
        directory.setCommitted(true);
        oma.update(directory);
    }

    @Override
    protected void rollbackDirectory(SQLDirectory directory) {
        try {
            oma.deleteStatement(SQLDirectory.class).where(SQLDirectory.ID, directory.getId()).executeUpdate();
        } catch (SQLException e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .withSystemErrorMessage("Layer2/SQL: Failed to rollback directory %s (%s) in %s: %s (%s)",
                                                    directory.getName(),
                                                    directory.getId(),
                                                    spaceName)
                            .handle();
        }
    }

    @Override
    protected SQLDirectory lookupDirectoryById(String idAsString) {
        return oma.find(SQLDirectory.class, idAsString).orElse(null);
    }

    @Override
    public Blob createTemporaryBlob() {
        SQLBlob result = new SQLBlob();
        result.setSpaceName(spaceName);
        result.setTemporary(true);
        result.setCommitted(true);
        oma.update(result);

        return result;
    }

    @Override
    public Blob createTemporaryBlob(String tenantId) {
        SQLBlob result = new SQLBlob();
        result.setSpaceName(spaceName);
        result.setTenantId(tenantId);
        result.setTemporary(true);
        result.setCommitted(true);
        oma.update(result);

        return result;
    }

    @Override
    public Optional<? extends Blob> findAttachedBlobByName(String referencingEntity, String filename) {
        if (Strings.isEmpty(referencingEntity)) {
            return Optional.empty();
        }

        return oma.select(SQLBlob.class)
                  .eq(SQLBlob.SPACE_NAME, spaceName)
                  .eq(effectiveFilenameMapping(), effectiveFilename(filename))
                  .eq(SQLBlob.REFERENCE, referencingEntity)
                  .eq(SQLBlob.REFERENCE_DESIGNATOR, null)
                  .eq(SQLBlob.DELETED, false)
                  .eq(SQLBlob.COMMITTED, true)
                  .first();
    }

    private String effectiveFilename(String filename) {
        if (useNormalizedNames && filename != null) {
            return filename.toLowerCase();
        }

        return filename;
    }

    private Mapping effectiveFilenameMapping() {
        return useNormalizedNames ? SQLBlob.NORMALIZED_FILENAME : SQLBlob.FILENAME;
    }

    @Override
    public List<? extends Blob> findAttachedBlobs(String referencingEntity) {
        if (Strings.isEmpty(referencingEntity)) {
            return Collections.emptyList();
        }

        return oma.select(SQLBlob.class)
                  .eq(SQLBlob.SPACE_NAME, spaceName)
                  .eq(SQLBlob.REFERENCE, referencingEntity)
                  .eq(SQLBlob.REFERENCE_DESIGNATOR, null)
                  .eq(SQLBlob.COMMITTED, true)
                  .eq(SQLBlob.DELETED, false)
                  .orderAsc(SQLBlob.FILENAME)
                  .queryList();
    }

    @Override
    public void deleteAttachedBlobs(String referencingEntity) {
        if (Strings.isEmpty(referencingEntity)) {
            return;
        }
        try {
            oma.updateStatement(SQLBlob.class)
               .set(SQLBlob.DELETED, true)
               .where(SQLBlob.SPACE_NAME, spaceName)
               .where(SQLBlob.REFERENCE, referencingEntity)
               .where(SQLBlob.REFERENCE_DESIGNATOR, null)
               .executeUpdate();
        } catch (SQLException e) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(e)
                      .withSystemErrorMessage(
                              "Layer 2/SQL: An error occured, when marking the objects referenced to '%s' as deleted: %s (%s)",
                              referencingEntity)
                      .handle();
        }
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
        try {
            UpdateStatement updateStatement = oma.updateStatement(SQLBlob.class)
                                                 .set(SQLBlob.DELETED, true)
                                                 .where(SQLBlob.SPACE_NAME, spaceName)
                                                 .where(SQLBlob.REFERENCE, referencingEntity)
                                                 .where(SQLBlob.REFERENCE_DESIGNATOR, referenceDesignator);

            if (Strings.isFilled(excludedBlobKey)) {
                updateStatement.where(SQLBlob.BLOB_KEY, Operator.NE, excludedBlobKey);
            }

            updateStatement.executeUpdate();
        } catch (SQLException e) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(e)
                      .withSystemErrorMessage(
                              "Layer 2/SQL: An error occured, when marking the objects referenced to '%s' via '%s' as deleted: %s (%s)",
                              referencingEntity,
                              referenceDesignator)
                      .handle();
        }
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

        try {
            // Remove any previous references...
            oma.updateStatement(SQLBlob.class)
               .set(SQLBlob.REFERENCE, null)
               .set(SQLBlob.REFERENCE_DESIGNATOR, null)
               .where(SQLBlob.SPACE_NAME, spaceName)
               .where(SQLBlob.REFERENCE, referencingEntity)
               .where(SQLBlob.REFERENCE_DESIGNATOR, referenceDesignator)
               .where(SQLBlob.TEMPORARY, false)
               .where(SQLBlob.COMMITTED, true)
               .where(SQLBlob.DELETED, false)
               .executeUpdate();

            // Place new reference...
            int numChanges = oma.updateStatement(SQLBlob.class)
                                .set(SQLBlob.REFERENCE, referencingEntity)
                                .set(SQLBlob.REFERENCE_DESIGNATOR, referenceDesignator)
                                .where(SQLBlob.SPACE_NAME, spaceName)
                                .where(SQLBlob.BLOB_KEY, objectKey)
                                .where(SQLBlob.REFERENCE, null)
                                .where(SQLBlob.REFERENCE_DESIGNATOR, null)
                                .where(SQLBlob.TEMPORARY, false)
                                .where(SQLBlob.COMMITTED, true)
                                .where(SQLBlob.DELETED, false)
                                .executeUpdate();
            if (numChanges == 0) {
                throw Exceptions.handle()
                                .to(StorageUtils.LOG)
                                .withSystemErrorMessage(
                                        "Layer 2/SQL: An error occured, cannot reference '%s' from '%s' ('%s'): The blob is either deleted, temporary or already in use.",
                                        objectKey,
                                        referencingEntity,
                                        referenceDesignator)
                                .handle();
            }
        } catch (SQLException e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage(
                                    "Layer 2/SQL: An error occured, when referencing '%s' from '%s' ('%s'): %s (%s)",
                                    objectKey,
                                    referencingEntity,
                                    referenceDesignator)
                            .handle();
        }
    }

    @Override
    public Optional<? extends Blob> findAttachedBlobByType(String referencingEntity, String referenceDesignator) {
        return oma.select(SQLBlob.class)
                  .eq(SQLBlob.SPACE_NAME, spaceName)
                  .eq(SQLBlob.REFERENCE, referencingEntity)
                  .eq(SQLBlob.REFERENCE_DESIGNATOR, referenceDesignator)
                  .eq(SQLBlob.COMMITTED, true)
                  .eq(SQLBlob.DELETED, false)
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

        try {
            oma.updateStatement(SQLBlob.class)
               .set(SQLBlob.REFERENCE, referencingEntity)
               .set(SQLBlob.REFERENCE_DESIGNATOR, referenceDesignator)
               .set(SQLBlob.TEMPORARY, false)
               .where(SQLBlob.SPACE_NAME, spaceName)
               .where(SQLBlob.BLOB_KEY, objectKey)
               .where(SQLBlob.TEMPORARY, true)
               .executeUpdate();
        } catch (SQLException e) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(e)
                      .withSystemErrorMessage(
                              "Layer 2/SQL: An error occured, when marking the object '%s' as used: %s (%s)",
                              objectKey)
                      .handle();
        }
    }

    @Override
    public void markAsUsed(Blob blob) {
        try {
            if (blob == null) {
                return;
            }

            oma.updateStatement(SQLBlob.class)
               .set(SQLBlob.TEMPORARY, false)
               .where(SQLBlob.SPACE_NAME, spaceName)
               .where(SQLBlob.ID, ((SQLBlob) blob).getId())
               .where(SQLBlob.TEMPORARY, true)
               .executeUpdate();
        } catch (SQLException e) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(e)
                      .withSystemErrorMessage(
                              "Layer 2/SQL: An error occured, when marking the object '%s' as used: %s (%s)",
                              blob.getBlobKey())
                      .handle();
        }
    }

    @Override
    protected void markBlobAsDeleted(SQLBlob blob) {
        try {
            oma.updateStatement(SQLBlob.class)
               .set(SQLBlob.DELETED, true)
               .where(SQLBlob.ID, blob.getId())
               .executeUpdate();
        } catch (SQLException e) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(e)
                      .withSystemErrorMessage(
                              "Layer 2/SQL: An error occured, when marking the blob '%s' as deleted: %s (%s)",
                              blob.getBlobKey())
                      .handle();
        }
    }

    @Override
    protected void updateBlobName(SQLBlob blob, String newName) {
        blob.setFilename(newName);
        oma.update(blob);
    }

    @Override
    protected void updateBlobParent(SQLBlob blob, SQLDirectory newParent) {
        blob.getParentRef().setValue(newParent);
        oma.update(blob);
    }

    @Override
    protected void markDirectoryAsDeleted(SQLDirectory directory) {
        try {
            oma.updateStatement(SQLDirectory.class)
               .set(SQLDirectory.DELETED, true)
               .where(SQLDirectory.ID, directory.getId())
               .executeUpdate();
        } catch (SQLException e) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(e)
                      .withSystemErrorMessage(
                              "Layer 2/SQL: An error occured, when marking the directory '%s' as deleted: %s (%s)",
                              directory.getId())
                      .handle();
        }
    }

    @Override
    protected void updateDirectoryParent(SQLDirectory directory, SQLDirectory newParent) {
        directory.getParentRef().setValue(newParent);
        oma.update(directory);
    }

    @Override
    protected void updateDirectoryName(SQLDirectory directory, String newName) {
        directory.setDirectoryName(newName);
        oma.update(directory);
    }

    protected SQLDirectory fetchDirectoryParent(SQLDirectory directory) {
        return (SQLDirectory) fetchDirectoryById(directory.getParentRef().getIdAsString());
    }

    protected SQLDirectory fetchBlobParent(SQLBlob blob) {
        return (SQLDirectory) fetchDirectoryById(blob.getParentRef().getIdAsString());
    }

    @Nonnull
    @Override
    protected Optional<String> updateBlob(@Nonnull SQLBlob blob,
                                          @Nonnull String nextPhysicalId,
                                          long size,
                                          @Nullable String filename) throws Exception {
        int retries = UPDATE_BLOB_RETRIES;
        while (retries-- > 0) {
            UpdateStatement updateStatement = oma.updateStatement(SQLBlob.class)
                                                 .set(SQLBlob.PHYSICAL_OBJECT_KEY, nextPhysicalId)
                                                 .set(SQLBlob.SIZE, size)
                                                 .setToNow(SQLBlob.LAST_MODIFIED);
            if (Strings.isFilled(filename)) {
                filename = filename.trim();
                updateStatement.set(SQLBlob.FILENAME, filename)
                               .set(SQLBlob.NORMALIZED_FILENAME, filename.toLowerCase())
                               .set(SQLBlob.FILE_EXTENSION, Files.getFileExtension(filename.toLowerCase()));
            }

            int numUpdated = updateStatement.where(SQLBlob.ID, blob.getId())
                                            .where(SQLBlob.PHYSICAL_OBJECT_KEY, blob.getPhysicalObjectKey())
                                            .executeUpdate();
            if (numUpdated == 1) {
                // Also update in-memory to avoid an additional database fetch...
                String previousPhysicalObjectKey = blob.getPhysicalObjectKey();
                blob.setPhysicalObjectKey(nextPhysicalId);
                blob.setFilename(filename);
                blob.updateFilenameFields();
                blob.setSize(size);
                blob.setLastModified(LocalDateTime.now());

                return Optional.ofNullable(previousPhysicalObjectKey);
            } else if (retries > 0) {
                blob = oma.refreshOrFail(blob);
            }
        }

        throw new IllegalStateException(Strings.apply("Cannot update the contents after %s retries.",
                                                      UPDATE_BLOB_RETRIES));
    }

    protected boolean hasExistingChild(SQLDirectory parent, String childName) {
        if (childDirectoryQuery(parent, childName).exists()) {
            return true;
        }

        return childBlobQuery(parent, childName).exists();
    }

    private SmartQuery<SQLDirectory> childDirectoryQuery(SQLDirectory parent, String childName) {
        return oma.select(SQLDirectory.class)
                  .eq(SQLDirectory.SPACE_NAME, spaceName)
                  .eq(SQLDirectory.PARENT, parent)
                  .eq(effectiveDirectoryNameMapping(), effectiveFilename(childName))
                  .eq(SQLDirectory.COMMITTED, true)
                  .eq(SQLDirectory.DELETED, false);
    }

    private Mapping effectiveDirectoryNameMapping() {
        return useNormalizedNames ? SQLDirectory.NORMALIZED_DIRECTORY_NAME : SQLDirectory.DIRECTORY_NAME;
    }

    protected Optional<SQLDirectory> findExistingChildDirectory(SQLDirectory parent, String childName) {
        return childDirectoryQuery(parent, childName).first();
    }

    @Override
    protected SQLDirectory findAnyChildDirectory(SQLDirectory parent, String childName) {
        return oma.select(SQLDirectory.class)
                  .eq(SQLDirectory.SPACE_NAME, spaceName)
                  .eq(SQLDirectory.PARENT, parent)
                  .eq(effectiveDirectoryNameMapping(), effectiveFilename(childName))
                  .eq(SQLDirectory.DELETED, false)
                  .queryFirst();
    }

    @Override
    protected SQLDirectory createChildDirectory(SQLDirectory parent, String childName) {
        SQLDirectory newDirectory = new SQLDirectory();
        newDirectory.setSpaceName(spaceName);
        newDirectory.getParentRef().setValue(parent);
        newDirectory.setTenantId(parent.getTenantId());
        newDirectory.setDirectoryName(childName);
        newDirectory.setCommitted(false);
        oma.update(newDirectory);

        return newDirectory;
    }

    @Override
    protected boolean isChildDirectoryUnique(SQLDirectory parent, String childName, SQLDirectory childDirectory) {
        return oma.select(SQLDirectory.class)
                  .eq(SQLDirectory.SPACE_NAME, spaceName)
                  .eq(SQLDirectory.PARENT, parent)
                  .eq(effectiveDirectoryNameMapping(), effectiveFilename(childName))
                  .eq(SQLDirectory.DELETED, false)
                  .count() == 1;
    }

    protected void listChildDirectories(SQLDirectory parent,
                                        String prefixFilter,
                                        int maxResults,
                                        Predicate<? super Directory> childProcessor) {
        oma.select(SQLDirectory.class)
           .eq(SQLDirectory.SPACE_NAME, spaceName)
           .eq(SQLDirectory.PARENT, parent)
           .eq(SQLDirectory.COMMITTED, true)
           .eq(SQLDirectory.DELETED, false)
           .where(OMA.FILTERS.like(SQLDirectory.NORMALIZED_DIRECTORY_NAME)
                             .startsWith(prefixFilter)
                             .ignoreEmpty()
                             .build())
           .limit(maxResults)
           .iterate(childProcessor::test);
    }

    protected Optional<SQLBlob> findExistingChildBlob(SQLDirectory parent, String childName) {
        return childBlobQuery(parent, childName).first();
    }

    private SmartQuery<SQLBlob> childBlobQuery(SQLDirectory parent, String childName) {
        return oma.select(SQLBlob.class)
                  .eq(SQLBlob.SPACE_NAME, spaceName)
                  .eq(SQLBlob.PARENT, parent)
                  .eq(effectiveFilenameMapping(), effectiveFilename(childName))
                  .eq(SQLBlob.COMMITTED, true)
                  .eq(SQLBlob.DELETED, false);
    }

    @Override
    protected SQLBlob findAnyChildBlob(SQLDirectory parent, String childName) {
        return oma.select(SQLBlob.class)
                  .eq(SQLBlob.SPACE_NAME, spaceName)
                  .eq(SQLBlob.PARENT, parent)
                  .eq(effectiveFilenameMapping(), effectiveFilename(childName))
                  .eq(SQLBlob.DELETED, false)
                  .queryFirst();
    }

    @Override
    protected SQLBlob createChildBlob(SQLDirectory parent, String childName) {
        SQLBlob newBlob = new SQLBlob();
        newBlob.setSpaceName(spaceName);
        newBlob.getParentRef().setValue(parent);
        newBlob.setTenantId(parent.getTenantId());
        newBlob.setFilename(childName);
        newBlob.setCommitted(false);
        oma.update(newBlob);

        return newBlob;
    }

    @Override
    protected boolean isChildBlobUnique(SQLDirectory parent, String childName, SQLBlob childBlob) {
        return oma.select(SQLBlob.class)
                  .eq(SQLBlob.SPACE_NAME, spaceName)
                  .eq(SQLBlob.PARENT, parent)
                  .eq(effectiveFilenameMapping(), effectiveFilename(childName))
                  .eq(SQLBlob.DELETED, false)
                  .count() == 1;
    }

    @Override
    protected void commitBlob(SQLBlob blob) {
        blob.setCommitted(true);
        oma.update(blob);
    }

    @Override
    protected void rollbackBlob(SQLBlob blob) {
        oma.delete(blob);
    }

    @Override
    protected SQLBlob findAnyAttachedBlobByName(String referencingEntity, String filename) {
        return oma.select(SQLBlob.class)
                  .eq(SQLBlob.SPACE_NAME, spaceName)
                  .eq(effectiveFilenameMapping(), effectiveFilename(filename))
                  .eq(SQLBlob.REFERENCE, referencingEntity)
                  .eq(SQLBlob.REFERENCE_DESIGNATOR, null)
                  .eq(SQLBlob.DELETED, false)
                  .queryFirst();
    }

    @Override
    protected SQLBlob createAttachedBlobByName(String referencingEntity, String filename) {
        SQLBlob result = new SQLBlob();
        result.setSpaceName(spaceName);
        result.setFilename(filename);
        result.setReference(referencingEntity);
        result.setCommitted(false);
        oma.update(result);

        return result;
    }

    @Override
    protected boolean isAttachedBlobUnique(String referencingEntity, String filename, SQLBlob blob) {
        return oma.select(SQLBlob.class)
                  .eq(SQLBlob.SPACE_NAME, spaceName)
                  .eq(effectiveFilenameMapping(), effectiveFilename(filename))
                  .eq(SQLBlob.REFERENCE, referencingEntity)
                  .eq(SQLBlob.REFERENCE_DESIGNATOR, null)
                  .eq(SQLBlob.DELETED, false)
                  .count() == 1;
    }

    protected void listChildBlobs(SQLDirectory parent,
                                  String prefixFilter,
                                  Set<String> fileTypes,
                                  int maxResults,
                                  Predicate<? super Blob> childProcessor) {
        oma.select(SQLBlob.class)
           .eq(SQLBlob.SPACE_NAME, spaceName)
           .eq(SQLBlob.PARENT, parent)
           .eq(SQLBlob.DELETED, false)
           .where(OMA.FILTERS.like(SQLBlob.NORMALIZED_FILENAME).startsWith(prefixFilter).ignoreEmpty().build())
           .where(OMA.FILTERS.containsOne(SQLBlob.FILE_EXTENSION, fileTypes.toArray()).build())
           .limit(maxResults)
           .iterate(childProcessor::test);
    }

    protected List<? extends BlobVariant> fetchVariants(SQLBlob blob) {
        return oma.select(SQLVariant.class)
                  .eq(SQLVariant.SOURCE_BLOB, blob)
                  .ne(SQLVariant.PHYSICAL_OBJECT_KEY, null)
                  .orderAsc(SQLVariant.VARIANT_NAME)
                  .queryList();
    }

    @Override
    protected SQLVariant findVariant(SQLBlob blob, String variantName) {
        return oma.select(SQLVariant.class)
                  .eq(SQLVariant.SOURCE_BLOB, blob)
                  .ne(SQLVariant.PHYSICAL_OBJECT_KEY, null)
                  .eq(SQLVariant.VARIANT_NAME, variantName)
                  .queryFirst();
    }

    @Override
    protected SQLVariant createVariant(SQLBlob blob, String variantName) {
        SQLVariant variant = new SQLVariant();
        variant.getSourceBlob().setValue(blob);
        variant.setVariantName(variantName);
        variant.setQueuedForConversion(true);
        variant.setNode(CallContext.getNodeName());
        variant.setLastConversionAttempt(LocalDateTime.now());
        variant.setNumAttempts(1);
        oma.update(variant);
        return variant;
    }

    @Override
    protected boolean detectAndRemoveDuplicateVariant(SQLVariant variant, SQLBlob blob, String variantName) {
        if (oma.select(SQLVariant.class)
               .ne(SQLVariant.ID, variant.getId())
               .eq(SQLVariant.SOURCE_BLOB, blob)
               .eq(SQLVariant.VARIANT_NAME, variantName)
               .exists()) {
            oma.delete(variant);
            return true;
        }

        return false;
    }

    @Override
    protected boolean markConversionAttempt(SQLVariant variant) throws Exception {
        return oma.updateStatement(SQLVariant.class)
                  .set(SQLVariant.QUEUED_FOR_CONVERSION, true)
                  .set(SQLVariant.NODE, CallContext.getNodeName())
                  .setToNow(SQLVariant.LAST_CONVERSION_ATTEMPT)
                  .inc(SQLVariant.NUM_ATTEMPTS)
                  .where(SQLVariant.ID, variant.getId())
                  .where(SQLVariant.NUM_ATTEMPTS, variant.getNumAttempts())
                  .executeUpdate() == 1;
    }

    @Override
    protected void markConversionFailure(SQLVariant variant) {
        variant.setQueuedForConversion(false);
        oma.update(variant);
    }

    @Override
    protected void markConversionSuccess(SQLVariant variant, String physicalKey, long size) {
        variant.setQueuedForConversion(false);
        variant.setSize(size);
        variant.setPhysicalObjectKey(physicalKey);
        oma.update(variant);
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
            SmartQuery<SQLBlob> deleteQuery = oma.select(SQLBlob.class)
                                                 .eq(SQLBlob.SPACE_NAME, spaceName)
                                                 .where(OMA.FILTERS.lt(SQLBlob.LAST_MODIFIED,
                                                                       LocalDateTime.now().minusDays(retentionDays)))
                                                 .where(OMA.FILTERS.ltOrEmpty(SQLBlob.LAST_TOUCHED,
                                                                              LocalDateTime.now()
                                                                                           .minusDays(retentionDays)));
            if (isTouchTracking()) {
                deleteQuery.where(OMA.FILTERS.ltOrEmpty(SQLBlob.LAST_TOUCHED,
                                                        LocalDateTime.now().minusDays(retentionDays)));
            }

            deleteQuery.limit(256).delete();
        } catch (Exception e) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(e)
                      .withSystemErrorMessage("Layer 2/SQL: Failed to delete old blobs in %s: %s (%s)", spaceName)
                      .handle();
        }
    }

    protected void deleteTemporaryBlobs() {
        try {
            oma.select(SQLBlob.class)
               .eq(SQLBlob.SPACE_NAME, spaceName)
               .eq(SQLBlob.TEMPORARY, true)
               .where(OMA.FILTERS.lt(SQLBlob.LAST_MODIFIED, LocalDateTime.now().minusHours(4)))
               .limit(256)
               .delete();
        } catch (Exception e) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(e)
                      .withSystemErrorMessage("Layer 2/SQL: Failed to delete temporary blobs in %s: %s (%s)", spaceName)
                      .handle();
        }
    }

    @Override
    public void markTouched(Set<String> blobKeys) {
        try (BatchContext batchContext = new BatchContext(() -> "Mark SQLBlobs as touched", Duration.ofMinutes(1))) {
            UpdateQuery<SQLBlob> markTouchedQuery =
                    batchContext.updateQuery(SQLBlob.class, SQLBlob.BLOB_KEY).withUpdatedMappings(SQLBlob.LAST_TOUCHED);
            SQLBlob template = new SQLBlob();
            template.setLastTouched(LocalDateTime.now());
            for (String blobKey : blobKeys) {
                template.setBlobKey(blobKey);
                markTouchedQuery.update(template, false, false);
            }
        } catch (Exception e) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(e)
                      .withSystemErrorMessage("Layer 2/SQL: Failed to mark blobs in %s as touched: %s (%s)", spaceName)
                      .handle();
        }
    }
}
