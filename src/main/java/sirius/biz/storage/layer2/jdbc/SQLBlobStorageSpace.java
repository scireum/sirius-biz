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
import sirius.biz.storage.layer2.variants.BlobVariant;
import sirius.biz.storage.layer2.variants.ConversionProcess;
import sirius.biz.storage.util.StorageUtils;
import sirius.biz.web.BasePageHelper;
import sirius.biz.web.SQLPageHelper;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.Operator;
import sirius.db.jdbc.SmartQuery;
import sirius.db.jdbc.UpdateStatement;
import sirius.db.jdbc.batch.BatchContext;
import sirius.db.jdbc.batch.UpdateQuery;
import sirius.db.mixing.DateRange;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.Mixing;
import sirius.db.mixing.query.QueryField;
import sirius.kernel.async.CallContext;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.kernel.settings.Extension;
import sirius.web.http.WebContext;

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

    @Part
    private static StorageUtils storageUtils;

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
        } catch (SQLException _) {
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
            return storageUtils.sanitizePath(filename).toLowerCase();
        }

        return storageUtils.sanitizePath(filename);
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
        } catch (SQLException exception) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(exception)
                      .withSystemErrorMessage(
                              "Layer 2/SQL: An error occurred, when marking the objects referenced to '%s' as deleted: %s (%s)",
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
        } catch (SQLException exception) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(exception)
                      .withSystemErrorMessage(
                              "Layer 2/SQL: An error occurred, when marking the objects referenced to '%s' via '%s' as deleted: %s (%s)",
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
                                        "Layer 2/SQL: An error occurred, cannot reference '%s' from '%s' ('%s'): The blob is either deleted, temporary or already in use.",
                                        objectKey,
                                        referencingEntity,
                                        referenceDesignator)
                                .handle();
            }
        } catch (SQLException exception) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
                            .withSystemErrorMessage(
                                    "Layer 2/SQL: An error occurred, when referencing '%s' from '%s' ('%s'): %s (%s)",
                                    objectKey,
                                    referencingEntity,
                                    referenceDesignator)
                            .handle();
        }
    }

    @Override
    public void attachTemporaryBlob(String objectKey, String referencingEntity) {
        if (Strings.isEmpty(referencingEntity)) {
            return;
        }

        if (Strings.isEmpty(objectKey)) {
            return;
        }

        try {
            int numChanges = oma.updateStatement(SQLBlob.class)
                                .set(SQLBlob.REFERENCE, referencingEntity)
                                .set(SQLBlob.TEMPORARY, false)
                                .where(SQLBlob.SPACE_NAME, spaceName)
                                .where(SQLBlob.BLOB_KEY, objectKey)
                                .where(SQLBlob.REFERENCE, null)
                                .where(SQLBlob.REFERENCE_DESIGNATOR, null)
                                .where(SQLBlob.TEMPORARY, true)
                                .where(SQLBlob.COMMITTED, true)
                                .where(SQLBlob.DELETED, false)
                                .executeUpdate();
            if (numChanges == 0) {
                throw Exceptions.handle()
                                .to(StorageUtils.LOG)
                                .withSystemErrorMessage(
                                        "Layer 2/SQL: An error occurred, cannot reference '%s' from '%s: The blob is either deleted, not temporary or already in use.",
                                        objectKey,
                                        referencingEntity)
                                .handle();
            }
        } catch (SQLException exception) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
                            .withSystemErrorMessage(
                                    "Layer 2/SQL: An error occurred, when referencing '%s' from '%s: %s (%s)",
                                    objectKey,
                                    referencingEntity)
                            .handle();
        }
    }

    @Override
    public Optional<? extends Blob> findAttachedBlobByKey(String referencingEntity, String blobKey) {
        return oma.select(SQLBlob.class)
                  .eq(SQLBlob.SPACE_NAME, spaceName)
                  .eq(SQLBlob.BLOB_KEY, blobKey)
                  .eq(SQLBlob.REFERENCE, referencingEntity)
                  .eq(SQLBlob.COMMITTED, true)
                  .eq(SQLBlob.DELETED, false)
                  .first();
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
        } catch (SQLException exception) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(exception)
                      .withSystemErrorMessage(
                              "Layer 2/SQL: An error occurred, when marking the object '%s' as used: %s (%s)",
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
        } catch (SQLException exception) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(exception)
                      .withSystemErrorMessage(
                              "Layer 2/SQL: An error occurred, when marking the object '%s' as used: %s (%s)",
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
        } catch (SQLException exception) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(exception)
                      .withSystemErrorMessage(
                              "Layer 2/SQL: An error occurred, when marking the blob '%s' as deleted: %s (%s)",
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
    public void updateBlobReadOnlyFlag(SQLBlob blob, boolean readOnly) {
        try {
            oma.updateStatement(SQLBlob.class)
               .set(SQLBlob.READ_ONLY, readOnly)
               .where(SQLBlob.ID, blob.getId())
               .executeUpdate();
        } catch (SQLException exception) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(exception)
                      .withSystemErrorMessage(
                              "Layer 2/SQL: An error occurred, when updating the read-only flag of blob '%s' to %s: %s (%s)",
                              blob.getBlobKey(),
                              readOnly)
                      .handle();
        }
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
        } catch (SQLException exception) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(exception)
                      .withSystemErrorMessage(
                              "Layer 2/SQL: An error occurred, when marking the directory '%s' as deleted: %s (%s)",
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
    @SuppressWarnings("java:S2259")
    @Explain("String filled check is performed on filename.")
    protected Optional<String> updateBlob(@Nonnull SQLBlob blob,
                                          @Nonnull String nextPhysicalId,
                                          long size,
                                          @Nullable String filename,
                                          @Nullable String checksum) throws Exception {
        int retries = UPDATE_BLOB_RETRIES;
        while (retries-- > 0) {
            UpdateStatement updateStatement = oma.updateStatement(SQLBlob.class)
                                                 .set(SQLBlob.PHYSICAL_OBJECT_KEY, nextPhysicalId)
                                                 .set(SQLBlob.CHECKSUM, checksum)
                                                 .set(SQLBlob.SIZE, size)
                                                 .setToNow(SQLBlob.LAST_MODIFIED);
            if (Strings.isFilled(filename)) {
                filename = filename.trim();
                updateStatement.set(SQLBlob.FILENAME, filename)
                               .set(SQLBlob.NORMALIZED_FILENAME, filename.toLowerCase())
                               .set(SQLBlob.FILE_EXTENSION, Files.getFileExtension(filename.toLowerCase()));
            }

            String previousPhysicalObjectKey = blob.getPhysicalObjectKey();
            if (Strings.isFilled(previousPhysicalObjectKey)) {
                updateStatement.set(SQLBlob.CONTENT_UPDATED, true);
            } else {
                updateStatement.set(SQLBlob.CREATED, true);
            }

            int numUpdated = updateStatement.where(SQLBlob.ID, blob.getId())
                                            .where(SQLBlob.PHYSICAL_OBJECT_KEY, blob.getPhysicalObjectKey())
                                            .executeUpdate();
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
                blob = oma.refreshOrFail(blob);
            }
        }

        throw new IllegalStateException(Strings.apply("Cannot update the contents after %s retries.",
                                                      UPDATE_BLOB_RETRIES));
    }

    protected boolean hasExistingChild(SQLDirectory parent,
                                       String childName,
                                       Directory exemptedDirectory,
                                       Blob exemptedBlob) {
        return hasExistingChildDirectory(parent, childName, exemptedDirectory) || hasExistingChildBlob(parent,
                                                                                                       childName,
                                                                                                       exemptedBlob);
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
                             .startsWith(Value.of(prefixFilter).toLowerCase())
                             .ignoreEmpty()
                             .build())
           .limit(maxResults)
           .orderAsc(SQLDirectory.NORMALIZED_DIRECTORY_NAME)
           .iterate(childProcessor::test);
    }

    protected Optional<SQLBlob> findExistingChildBlob(SQLDirectory parent, String childName) {
        return childBlobQuery(parent, childName).first();
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
        SmartQuery<SQLBlob> query = oma.select(SQLBlob.class)
                                       .eq(SQLBlob.SPACE_NAME, spaceName)
                                       .eq(SQLBlob.PARENT, parent)
                                       .eq(SQLBlob.COMMITTED, true)
                                       .eq(SQLBlob.DELETED, false);

        if (fileTypes != null && !fileTypes.isEmpty()) {
            query.where(OMA.FILTERS.containsOne(SQLBlob.FILE_EXTENSION, fileTypes.toArray()).build());
        }
        query.where(OMA.FILTERS.or(OMA.FILTERS.like(SQLBlob.NORMALIZED_FILENAME)
                                              .startsWith(prefixFilter)
                                              .ignoreEmpty()
                                              .build(),
                                   OMA.FILTERS.like(SQLBlob.FILE_EXTENSION)
                                              .startsWith(prefixFilter)
                                              .ignoreEmpty()
                                              .build()));

        query.limit(maxResults);

        if (sortByLastModified) {
            query.orderDesc(SQLBlob.LAST_MODIFIED);
        } else {
            query.orderAsc(SQLBlob.NORMALIZED_FILENAME);
        }

        query.iterate(childProcessor::test);
    }

    protected BasePageHelper<? extends Blob, ?, ?, ?> queryChildBlobsAsPage(SQLDirectory parent,
                                                                            WebContext webContext) {
        SmartQuery<SQLBlob> blobsQuery = oma.select(SQLBlob.class)
                                            .eq(SQLBlob.SPACE_NAME, spaceName)
                                            .eq(SQLBlob.PARENT, parent)
                                            .eq(SQLBlob.COMMITTED, true)
                                            .eq(SQLBlob.DELETED, false);

        SQLPageHelper<SQLBlob> pageHelper = SQLPageHelper.withQuery(blobsQuery)
                                                         .withContext(webContext)
                                                         .withSearchFields(QueryField.startsWith(SQLBlob.NORMALIZED_FILENAME),
                                                                           QueryField.startsWith(SQLBlob.FILE_EXTENSION));

        pageHelper.addQueryFacet(SQLBlob.FILE_EXTENSION.getName(),
                                 NLS.get("Blob.fileExtension"),
                                 query -> query.copy().distinctFields(SQLBlob.FILE_EXTENSION).asSQLQuery());
        pageHelper.addTimeFacet(SQLBlob.LAST_MODIFIED.getName(),
                                NLS.get("Blob.lastModified"),
                                false,
                                DateRange.LAST_TWO_HOURS,
                                DateRange.TODAY,
                                DateRange.YESTERDAY,
                                DateRange.LAST_WEEK,
                                DateRange.LAST_MONTH,
                                DateRange.THIS_YEAR,
                                DateRange.LAST_YEAR);
        if (touchTracking) {
            pageHelper.addTimeFacet(SQLBlob.LAST_TOUCHED.getName(),
                                    NLS.get("Blob.lastTouched"),
                                    false,
                                    DateRange.LAST_TWO_HOURS,
                                    DateRange.TODAY,
                                    DateRange.YESTERDAY,
                                    DateRange.LAST_WEEK,
                                    DateRange.LAST_MONTH,
                                    DateRange.THIS_YEAR,
                                    DateRange.LAST_YEAR);
        }

        if (sortByLastModified) {
            pageHelper.addSortFacet(Tuple.create("$BlobStorageSpace.sortByLastModified",
                                                 query -> query.orderDesc(SQLBlob.LAST_MODIFIED)),
                                    Tuple.create("$BlobStorageSpace.sortByFilename",
                                                 query -> query.orderAsc(SQLBlob.NORMALIZED_FILENAME)));
        } else {
            pageHelper.addSortFacet(Tuple.create("$BlobStorageSpace.sortByFilename",
                                                 query -> query.orderAsc(SQLBlob.NORMALIZED_FILENAME)),
                                    Tuple.create("$BlobStorageSpace.sortByLastModified",
                                                 query -> query.orderDesc(SQLBlob.LAST_MODIFIED)));
        }

        return pageHelper;
    }

    protected List<? extends BlobVariant> fetchVariants(SQLBlob blob) {
        return oma.select(SQLVariant.class)
                  .eq(SQLVariant.SOURCE_BLOB, blob)
                  .orderAsc(SQLVariant.VARIANT_NAME)
                  .queryList();
    }

    @Override
    protected SQLVariant findCompletedVariant(SQLBlob blob, String variantName) {
        return oma.select(SQLVariant.class)
                  .eq(SQLVariant.SOURCE_BLOB, blob)
                  .ne(SQLVariant.PHYSICAL_OBJECT_KEY, null)
                  .eq(SQLVariant.VARIANT_NAME, variantName)
                  .queryFirst();
    }

    @Override
    protected SQLVariant findAnyVariant(SQLBlob blob, String variantName) {
        return oma.select(SQLVariant.class)
                  .eq(SQLVariant.SOURCE_BLOB, blob)
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
    protected SQLVariant createVariant(SQLBlob blob,
                                       String variantName,
                                       String physicalObjectKey,
                                       long size,
                                       @Nullable String checksum) {
        SQLVariant variant = new SQLVariant();
        variant.getSourceBlob().setValue(blob);
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
    protected void markConversionFailure(SQLVariant variant, ConversionProcess conversionProcess) {
        try {
            oma.updateStatement(SQLVariant.class)
               .set(SQLVariant.QUEUED_FOR_CONVERSION, false)
               .set(SQLVariant.CONVERSION_DURATION, conversionProcess.getConversionDuration())
               .set(SQLVariant.QUEUE_DURATION, conversionProcess.getQueueDuration())
               .set(SQLVariant.TRANSFER_DURATION, conversionProcess.getTransferDuration())
               .where(SQLVariant.ID, variant.getId())
               .executeUpdate();
        } catch (SQLException exception) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(exception)
                      .withSystemErrorMessage(
                              "Layer 2/SQL: An error occurred, when marking the variant '%s' of blob '%s' as failed: %s (%s)",
                              variant.getIdAsString(),
                              variant.getSourceBlob().getIdAsString())
                      .handle();
        }
    }

    @Override
    protected void markConversionSuccess(SQLVariant variant, String physicalKey, ConversionProcess conversionProcess) {
        try {
            String checksum = computeConversionCheckSum(conversionProcess);
            UpdateStatement updater = oma.updateStatement(SQLVariant.class)
                                         .set(SQLVariant.QUEUED_FOR_CONVERSION, false)
                                         .set(SQLVariant.PHYSICAL_OBJECT_KEY, physicalKey)
                                         .set(SQLVariant.CHECKSUM, checksum)
                                         .set(SQLVariant.CHECKSUM, computeConversionCheckSum(conversionProcess))
                                         .set(SQLVariant.SIZE,
                                              conversionProcess.getResultFileHandle().getFile().length())
                                         .set(SQLVariant.CONVERSION_DURATION, conversionProcess.getConversionDuration())
                                         .set(SQLVariant.QUEUE_DURATION, conversionProcess.getQueueDuration())
                                         .set(SQLVariant.TRANSFER_DURATION, conversionProcess.getTransferDuration())
                                         .where(SQLVariant.ID, variant.getId());
            updater.executeUpdate();
        } catch (SQLException exception) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(exception)
                      .withSystemErrorMessage(
                              "Layer 2/SQL: An error occurred, when marking the variant '%s' of blob '%s' as converted: %s (%s)",
                              variant.getIdAsString(),
                              variant.getSourceBlob().getIdAsString())
                      .handle();
        }
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
            oma.select(SQLBlob.class)
               .eq(SQLBlob.SPACE_NAME, spaceName)
               .eq(SQLBlob.DELETED, false)
               .where(OMA.FILTERS.lt(SQLBlob.LAST_MODIFIED, LocalDateTime.now().minusDays(retentionDays)))
               .where(OMA.FILTERS.ltOrEmpty(SQLBlob.LAST_TOUCHED, LocalDateTime.now().minusDays(retentionDays)))
               .streamBlockwise()
               .forEach(this::markBlobAsDeleted);
        } catch (Exception exception) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(exception)
                      .withSystemErrorMessage("Layer 2/SQL: Failed to delete old blobs in %s: %s (%s)", spaceName)
                      .handle();
        }
    }

    protected void deleteTemporaryBlobs() {
        try {
            oma.select(SQLBlob.class)
               .eq(SQLBlob.SPACE_NAME, spaceName)
               .eq(SQLBlob.DELETED, false)
               .eq(SQLBlob.TEMPORARY, true)
               .where(OMA.FILTERS.lt(SQLBlob.LAST_MODIFIED, LocalDateTime.now().minusHours(4)))
               .streamBlockwise()
               .forEach(this::markBlobAsDeleted);
        } catch (Exception exception) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(exception)
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
        } catch (Exception exception) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(exception)
                      .withSystemErrorMessage("Layer 2/SQL: Failed to mark blobs in %s as touched: %s (%s)", spaceName)
                      .handle();
        }
    }

    @Override
    protected void purgeVariantFromCache(SQLBlob blob, String variantName) {
        blobKeyToPhysicalCache.remove(buildCacheLookupKey(blob.getBlobKey(), variantName));
    }

    private boolean hasExistingChildDirectory(SQLDirectory parent, String childName, Directory exemptedDirectory) {
        SmartQuery<SQLDirectory> childDirectoryQuery = childDirectoryQuery(parent, childName);
        if (exemptedDirectory != null) {
            childDirectoryQuery.ne(SQLDirectory.ID, ((SQLDirectory) exemptedDirectory).getId());
        }
        return childDirectoryQuery.exists();
    }

    private boolean hasExistingChildBlob(SQLDirectory parent, String childName, Blob exemptedBlob) {
        SmartQuery<SQLBlob> childBlobQuery = childBlobQuery(parent, childName);
        if (exemptedBlob != null) {
            childBlobQuery.ne(SQLBlob.BLOB_KEY, exemptedBlob.getBlobKey());
        }
        return childBlobQuery.exists();
    }

    private SmartQuery<SQLDirectory> childDirectoryQuery(SQLDirectory parent, String childName) {
        return oma.select(SQLDirectory.class)
                  .eq(SQLDirectory.SPACE_NAME, spaceName)
                  .eq(SQLDirectory.PARENT, parent)
                  .eq(effectiveDirectoryNameMapping(), effectiveFilename(childName))
                  .eq(SQLDirectory.COMMITTED, true)
                  .eq(SQLDirectory.DELETED, false);
    }

    private SmartQuery<SQLBlob> childBlobQuery(SQLDirectory parent, String childName) {
        return oma.select(SQLBlob.class)
                  .eq(SQLBlob.SPACE_NAME, spaceName)
                  .eq(SQLBlob.PARENT, parent)
                  .eq(effectiveFilenameMapping(), effectiveFilename(childName))
                  .eq(SQLBlob.COMMITTED, true)
                  .eq(SQLBlob.DELETED, false);
    }

    private Mapping effectiveDirectoryNameMapping() {
        return useNormalizedNames ? SQLDirectory.NORMALIZED_DIRECTORY_NAME : SQLDirectory.DIRECTORY_NAME;
    }
}
