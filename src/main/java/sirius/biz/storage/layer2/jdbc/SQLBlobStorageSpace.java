/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2.jdbc;

import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer2.BasicBlobStorageSpace;
import sirius.biz.storage.layer2.Blob;
import sirius.biz.storage.layer2.BlobRevision;
import sirius.biz.storage.layer2.BlobVariant;
import sirius.biz.storage.layer2.Directory;
import sirius.biz.storage.util.StorageUtils;
import sirius.biz.storage.util.WatchableInputStream;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SmartQuery;
import sirius.db.mixing.Mixing;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Wait;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class SQLBlobStorageSpace extends BasicBlobStorageSpace<SQLBlob, SQLDirectory> {

    @Part
    private static OMA oma;

    @Part
    private static Mixing mixing;

    protected SQLBlobStorageSpace(String name, boolean browsable, boolean readonly) {
        super(name, browsable, readonly);
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
                  .first();
    }

    @Override
    protected SQLDirectory findRoot(String tenantId) {
        return oma.select(SQLDirectory.class)
                  .fields(SQLDirectory.ID)
                  .eq(SQLDirectory.SPACE_NAME, spaceName)
                  .eq(SQLDirectory.TENANT_ID, tenantId)
                  .eq(SQLDirectory.PARENT, null)
                  .queryFirst();
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
    protected SQLDirectory lookupDirectoryById(String idAsString) {
        return oma.find(SQLDirectory.class, idAsString).orElse(null);
    }

    @Override
    public Blob createTemporaryBlob() {
        SQLBlob result = new SQLBlob();
        result.setSpaceName(spaceName);
        result.setTemporary(true);
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
                  .eq(SQLBlob.FILENAME, filename)
                  .eq(SQLBlob.REFERENCE, referencingEntity)
                  .eq(SQLBlob.REFERENCE_DESIGNATOR, null)
                  .eq(SQLBlob.DELETED, false)
                  .first();
    }

    @Override
    public Blob findOrCreateAttachedBlobByName(String referencingEntity, String filename) {
        if (Strings.isEmpty(referencingEntity)) {
            throw new IllegalArgumentException("referencingEntity must not be empty");
        }

        Optional<? extends Blob> existingObject = oma.select(SQLBlob.class)
                                                     .eq(SQLBlob.SPACE_NAME, spaceName)
                                                     .eq(SQLBlob.FILENAME, filename)
                                                     .eq(SQLBlob.REFERENCE, referencingEntity)
                                                     .eq(SQLBlob.REFERENCE_DESIGNATOR, null)
                                                     .eq(SQLBlob.DELETED, false)
                                                     .first();
        if (existingObject.isPresent()) {
            return existingObject.get();
        }

        SQLBlob result = new SQLBlob();
        result.setSpaceName(spaceName);
        result.setFilename(filename);
        result.setReference(referencingEntity);
        oma.update(result);

        return result;
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
                  .orderAsc(SQLBlob.FILENAME)
                  .queryList();
    }

    @Override
    public void deleteAttachedBlobs(String referencingEntity) {
        if (Strings.isEmpty(referencingEntity)) {
            return;
        }

        oma.select(SQLBlob.class)
           .eq(SQLBlob.SPACE_NAME, spaceName)
           .eq(SQLBlob.REFERENCE, referencingEntity)
           .eq(SQLBlob.REFERENCE_DESIGNATOR, null)
           .delete();
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

        SmartQuery<SQLBlob> qry = oma.select(SQLBlob.class)
                                     .eq(SQLBlob.SPACE_NAME, spaceName)
                                     .eq(SQLBlob.REFERENCE, referencingEntity)
                                     .eq(SQLBlob.REFERENCE_DESIGNATOR, referenceDesignator);
        if (Strings.isFilled(excludedBlobKey)) {
            qry.ne(SQLBlob.BLOB_KEY, excludedBlobKey);
        }

        qry.delete();
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
            oma.getDatabase(Mixing.DEFAULT_REALM)
               .createQuery("UPDATE sqlblob"
                            + " SET reference=${reference}, referenceDesignator=${designator}, temporary=${temporary}"
                            + " WHERE spaceName=${spaceName}"
                            + "   AND blobKey=${blobKey}"
                            + "   AND temporary=${isTemporary}")
               .set("reference", referencingEntity)
               .set("designator", referenceDesignator)
               .set("temporary", false)
               .set("spaceName", spaceName)
               .set("blobKey", objectKey)
               .set("isTemporary", true)
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
    public long getNumberOfDirectories(@Nullable String tenantId) {
        try {
            return oma.getSecondaryDatabase(mixing.getDescriptor(SQLDirectory.class).getRealm())
                      .createQuery("SELECT count(*) as numDirectories"
                                   + " FROM sqldirectory"
                                   + " WHERE spaceName = ${spaceName}"
                                   + " [AND tenantId = ${tenantId}]")
                      .set("spaceName", spaceName)
                      .set("tenantId", tenantId)
                      .queryFirst()
                      .getValue("numDirectories")
                      .asLong(0);
        } catch (SQLException e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 2/SQL: Cannot count the number of directories of %s: %s (%s)",
                                                    spaceName)
                            .handle();
        }
    }

    @Override
    public long getNumberOfVisibleBlobs(@Nullable String tenantId) {
        try {
            return oma.getSecondaryDatabase(mixing.getDescriptor(SQLBlob.class).getRealm())
                      .createQuery("SELECT count(*) as numObjects"
                                   + " FROM sqlblob"
                                   + " WHERE spaceName = ${spaceName}"
                                   + " [AND tenantId = ${tenantId}]"
                                   + " AND parent IS NOT NULL")
                      .set("spaceName", spaceName)
                      .set("tenantId", tenantId)
                      .queryFirst()
                      .getValue("numObjects")
                      .asLong(0);
        } catch (SQLException e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 2/SQL: Cannot count all visible blobs of %s: %s (%s)",
                                                    spaceName)
                            .handle();
        }
    }

    @Override
    public long getSizeOfVisibleBlobs(@Nullable String tenantId) {
        try {
            return oma.getSecondaryDatabase(mixing.getDescriptor(SQLBlob.class).getRealm())
                      .createQuery("SELECT sum(size) as totalSize"
                                   + " FROM sqlblob"
                                   + " WHERE spaceName = ${spaceName}"
                                   + " [AND tenantId = ${tenantId}]"
                                   + " AND parent IS NOT NULL")
                      .set("spaceName", spaceName)
                      .set("tenantId", tenantId)
                      .queryFirst()
                      .getValue("totalSize")
                      .asLong(0);
        } catch (SQLException e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage(
                                    "Layer 2/SQL: Cannot determine the size of all visible blobs of %s: %s (%s)",
                                    spaceName)
                            .handle();
        }
    }

    @Override
    public long getNumberOfReferencedBlobs() {
        try {
            return oma.getSecondaryDatabase(mixing.getDescriptor(SQLBlob.class).getRealm())
                      .createQuery("SELECT count(*) as numObjects"
                                   + " FROM sqlblob"
                                   + " WHERE spaceName = ${spaceName}"
                                   + " AND reference IS NOT NULL")
                      .set("spaceName", spaceName)
                      .queryFirst()
                      .getValue("numObjects")
                      .asLong(0);
        } catch (SQLException e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 2/SQL: Cannot count all referenced blobs of %s: %s (%s)",
                                                    spaceName)
                            .handle();
        }
    }

    @Override
    public long getSizeOfReferencedBlobs() {
        try {
            return oma.getSecondaryDatabase(mixing.getDescriptor(SQLBlob.class).getRealm())
                      .createQuery("SELECT sum(size) as totalSize"
                                   + " FROM sqlblob"
                                   + " WHERE spaceName = ${spaceName}"
                                   + " AND reference IS NOT NULL")
                      .set("spaceName", spaceName)
                      .queryFirst()
                      .getValue("totalSize")
                      .asLong(0);
        } catch (SQLException e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage(
                                    "Layer 2/SQL: Cannot determine the size of all referenced blobs of %s: %s (%s)",
                                    spaceName)
                            .handle();
        }
    }

    @Override
    public long getNumberOfBlobs() {
        try {
            return oma.getSecondaryDatabase(mixing.getDescriptor(SQLBlob.class).getRealm())
                      .createQuery("SELECT count(*) as numObjects"
                                   + " FROM sqlblob"
                                   + " WHERE spaceName = ${spaceName}")
                      .set("spaceName", spaceName)
                      .queryFirst()
                      .getValue("numObjects")
                      .asLong(0);
        } catch (SQLException e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 2/SQL: Cannot count all blobs of %s: %s (%s)", spaceName)
                            .handle();
        }
    }

    @Override
    public long getSizeOfBlobs() {
        try {
            return oma.getSecondaryDatabase(mixing.getDescriptor(SQLBlob.class).getRealm())
                      .createQuery("SELECT sum(size) as totalSize"
                                   + " FROM sqlblob"
                                   + " WHERE spaceName = ${spaceName}")
                      .set("spaceName", spaceName)
                      .queryFirst()
                      .getValue("totalSize")
                      .asLong(0);
        } catch (SQLException e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 2/SQL: Cannot determine the size of all blobs of %s: %s (%s)",
                                                    spaceName)
                            .handle();
        }
    }

    protected void hide(SQLBlob blob) {
        try {
            oma.getDatabase(Mixing.DEFAULT_REALM)
               .createQuery("UPDATE sqlblob"
                            + " SET hidden=${hidden}"
                            + " WHERE spaceName=${spaceName}"
                            + "   AND blobKey=${blobKey}")
               .set("hidden", true)
               .set("spaceName", spaceName)
               .set("blobKey", blob.getBlobKey())
               .executeUpdate();
        } catch (SQLException e) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .error(e)
                      .withSystemErrorMessage(
                              "Layer 2/SQL: An error occured, when marking the blob '%s' as hidden: %s (%s)",
                              blob.getBlobKey())
                      .handle();
        }
    }

    protected void delete(SQLBlob blob) {
        //TODO oma API
        try {
            oma.getDatabase(Mixing.DEFAULT_REALM)
               .createQuery("UPDATE sqlmanagedobject"
                            + " SET deleted=${deleted}"
                            + " WHERE spaceName=${spaceName}"
                            + "   AND blobKey=${blobKey}")
               .set("deleted", true)
               .set("spaceName", spaceName)
               .set("blobKey", blob.getBlobKey())
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

    protected boolean move(SQLBlob object, @Nullable SQLDirectory newParent) {
        //TODO handle null properly
        throw new UnsupportedOperationException();
//        Objects.requireNonNull(newParent);
//        if (!Strings.areEqual(name, (SQLManagedDirectory)newParent).getSpaceName()) {
//
//        }
    }

    protected boolean rename(SQLBlob object, String newName) {
        throw new UnsupportedOperationException();
    }

    protected void deleteDirectory(SQLDirectory directory) {
        throw new UnsupportedOperationException();
    }

    protected void moveDirectory(SQLDirectory directory, SQLDirectory newParent) {
        //TODO assert same space
        SQLDirectory check = newParent;
        while (check != null && !Objects.equals(directory, check)) {
            check = fetchDirectoryParent(check);
        }

        if (check != null) {
            //TODO loop
            throw new IllegalArgumentException("Nenene");
        }

        directory = oma.tryRefresh(directory);
        directory.getParentRef().setValue(newParent);
        oma.update(directory);
        directoryByIdCache.remove(directory.getIdAsString());
    }

    protected void renameDirectory(SQLDirectory directory, String newName) {
        directory = oma.tryRefresh(directory);
        directory.setDirectoryName(newName);
        oma.update(directory);
        directoryByIdCache.remove(directory.getIdAsString());
    }

    protected void updateContent(SQLBlob blob, String filename, File file) {
        String nextPhysicalId = keyGenerator.generateId();
        try {
            getPhysicalSpace().upload(nextPhysicalId, file);
            blob = oma.refreshOrFail(blob);
            blob.setSize(file.length());
            if (Strings.isFilled(filename)) {
                blob.setFilename(filename);
            }

            blob.setLastModified(LocalDateTime.now());
            blob.setPhysicalObjectId(nextPhysicalId);
            oma.update(blob);
            nextPhysicalId = null;
        } catch (Exception e) {
            try {
                getPhysicalSpace().delete(nextPhysicalId);
            } catch (Exception ex) {
                Exceptions.ignore(ex);
            }

            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 2/SQL: Cannot update the contents of %s: %s (%s)",
                                                    blob.getBlobKey())
                            .handle();
        }
    }

    protected void updateContent(SQLBlob blob, String filename, InputStream data, long contentLength) {
        String nextPhysicalId = keyGenerator.generateId();
        try {
            getPhysicalSpace().upload(nextPhysicalId, data, contentLength);
            blob = oma.refreshOrFail(blob);
            blob.setSize(contentLength);
            if (Strings.isFilled(filename)) {
                blob.setFilename(filename);
            }
            blob.setLastModified(LocalDateTime.now());
            blob.setPhysicalObjectId(nextPhysicalId);
            oma.update(blob);
            nextPhysicalId = null;
        } catch (Exception e) {
            try {
                getPhysicalSpace().delete(nextPhysicalId);
            } catch (Exception ex) {
                Exceptions.ignore(ex);
            }

            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 2/SQL: Cannot update the contents of %s: %s (%s)",
                                                    blob.getBlobKey())
                            .handle();
        }
    }

    protected OutputStream createOutputStream(SQLBlob blob, String filename) {
        try {
            return utils.createLocalBuffer(file -> {
                try {
                    updateContent(blob, filename, file);
                } finally {
                    Files.delete(file);
                }
            });
        } catch (IOException e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage(
                                    "Layer 2/SQL: Cannot create a local buffer to provide an output stream for %s (%s): %s (%s)",
                                    blob.getId(),
                                    blob.getFilename())
                            .handle();
        }
    }

    protected SQLDirectory fetchDirectoryParent(SQLDirectory directory) {
        return (SQLDirectory) fetchDirectoryById(directory.getParentRef().getIdAsString());
    }

    protected SQLDirectory fetchBlobParent(SQLBlob blob) {
        return (SQLDirectory) fetchDirectoryById(blob.getParentRef().getIdAsString());
    }

    protected String determineDirectoryPath(SQLDirectory directory) {
        if (directory.getParentRef().isEmpty()) {
            return spaceName;
        }

        return determineDirectoryPath(fetchDirectoryParent(directory)) + "/" + directory.getDirectoryName();
    }

    protected String determineBlobPath(SQLBlob blob) {
        if (blob.getParentRef().isEmpty()) {
            return spaceName;
        }

        return determineDirectoryPath(fetchBlobParent(blob)) + "/" + blob.getFilename();
    }

    protected Optional<? extends Directory> findChildDirectory(SQLDirectory parent, String childName) {
        return oma.select(SQLDirectory.class)
                  .eq(SQLDirectory.SPACE_NAME, spaceName)
                  .eq(SQLDirectory.PARENT, parent)
                  .eq(SQLDirectory.DIRECTORY_NAME, childName)
                  .first();
    }

    protected Directory findOrCreateChildDirectory(SQLDirectory parent, String childName) {
        int retries = 3;
        while (retries-- > 0) {
            Optional<? extends Directory> directory = findChildDirectory(parent, childName);
            if (directory.isPresent()) {
                return directory.get();
            }

            SQLDirectory newDirectory = new SQLDirectory();
            newDirectory.setSpaceName(spaceName);
            newDirectory.getParentRef().setValue(parent);
            newDirectory.setTenantId(parent.getTenantId());
            newDirectory.setDirectoryName(childName);
            oma.update(newDirectory);

            if (oma.select(SQLDirectory.class)
                   .eq(SQLDirectory.SPACE_NAME, spaceName)
                   .eq(SQLDirectory.PARENT, parent)
                   .eq(SQLDirectory.DIRECTORY_NAME, childName)
                   .count() > 1) {
                oma.delete(newDirectory);
            } else {
                return newDirectory;
            }
            Wait.randomMillis(100, 600);
        }

        throw Exceptions.handle()
                        .to(StorageUtils.LOG)
                        .withSystemErrorMessage(
                                "Layer 2/SQL: Failed to create a child directory with name %s for parent directory %s (%s)",
                                childName,
                                parent.getDirectoryName(),
                                parent.getIdAsString())
                        .handle();
    }

    protected Optional<? extends Blob> findChildBlob(SQLDirectory parent, String childName) {
        return oma.select(SQLBlob.class)
                  .eq(SQLBlob.SPACE_NAME, spaceName)
                  .eq(SQLBlob.PARENT, parent)
                  .eq(SQLBlob.FILENAME, childName)
                  .ne(SQLBlob.DELETED, true)
                  .ne(SQLBlob.HIDDEN, true)
                  .first();
    }

    protected Blob findOrCreateChildBlob(SQLDirectory parent, String childName) {
        int retries = 3;

        while (retries-- > 0) {
            Optional<? extends Blob> blob = findChildBlob(parent, childName);
            if (blob.isPresent()) {
                return blob.get();
            }

            SQLBlob newBlob = new SQLBlob();
            newBlob.setSpaceName(spaceName);
            newBlob.getParentRef().setValue(parent);
            newBlob.setTenantId(parent.getTenantId());
            newBlob.setFilename(childName);
            oma.update(newBlob);

            if (oma.select(SQLBlob.class)
                   .eq(SQLBlob.SPACE_NAME, spaceName)
                   .eq(SQLBlob.PARENT, parent)
                   .eq(SQLBlob.FILENAME, childName)
                   .ne(SQLBlob.DELETED, true)
                   .ne(SQLBlob.HIDDEN, true)
                   .count() > 1) {
                oma.delete(newBlob);
            } else {
                return newBlob;
            }
            Wait.randomMillis(100, 600);
        }

        throw Exceptions.handle()
                        .to(StorageUtils.LOG)
                        .withSystemErrorMessage(
                                "Layer 2/SQL: Failed to create a child blob with name %s for parent directory %s (%s)",
                                childName,
                                parent.getDirectoryName(),
                                parent.getIdAsString())
                        .handle();
    }

    protected void listChildDirectories(SQLDirectory parent,
                                        String prefixFilter,
                                        Limit limit,
                                        Function<? super Directory, Boolean> childProcessor) {
        //TODO limit
        //TODO filtering
        oma.select(SQLDirectory.class)
           .eq(SQLDirectory.SPACE_NAME, spaceName)
           .eq(SQLDirectory.PARENT, parent)
           .iterate(child -> childProcessor.apply(child));
    }

    protected void listChildBlobs(SQLDirectory parent,
                                  String prefixFilter,
                                  Set<String> fileTypes,
                                  Limit limit,
                                  Function<? super Blob, Boolean> childProcessor) {
        //TODO limit
        //TODO filtering
        oma.select(SQLBlob.class)
           .eq(SQLBlob.SPACE_NAME, spaceName)
           .eq(SQLBlob.PARENT, parent)
           .iterate(child -> childProcessor.apply(child));
    }

    protected List<BlobVariant> fetchVariants(SQLBlob blob) {
        //TODO
        return Collections.emptyList();
    }

    protected List<BlobRevision> fetchRevisions(SQLBlob blob) {
        //TODO
        return Collections.emptyList();
    }

    public InputStream createInputStream(SQLBlob blob) {
        FileHandle fileHandle = blob.download().filter(FileHandle::exists).orElse(null);
        if (fileHandle == null) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .withSystemErrorMessage("Layer 2/SQL: Cannot obtain a file handle for %s (%s)",
                                                    blob.getId(),
                                                    blob.getFilename())
                            .handle();
        }

        WatchableInputStream result = null;
        try {
            result = new WatchableInputStream(fileHandle.getInputStream());
            result.getCompletionFuture().onSuccess(() -> fileHandle.close()).onFailure(e -> fileHandle.close());
        } catch (FileNotFoundException e) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Layer 2/SQL: Cannot obtain a file handle for %s (%s): %s (%s)",
                                                    blob.getId(),
                                                    blob.getFilename())
                            .handle();
        }

        return result;
    }
}
