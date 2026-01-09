/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2.jdbc;

import sirius.biz.storage.layer2.Blob;
import sirius.biz.storage.layer2.BlobStorage;
import sirius.biz.storage.layer2.Directory;
import sirius.biz.storage.layer2.OptimisticCreate;
import sirius.biz.storage.util.StorageUtils;
import sirius.biz.web.BasePageHelper;
import sirius.db.jdbc.SQLEntity;
import sirius.db.jdbc.SQLEntityRef;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Index;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.LowerCase;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;
import sirius.web.http.WebContext;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Stores the metadata of a {@link Directory} in the underlying JDBC database.
 * <p>
 * Note that all nontrivial methods delegate to the associated {@link SQLBlobStorageSpace}.
 */
@Framework(SQLBlobStorage.FRAMEWORK_JDBC_BLOB_STORAGE)
@Index(name = "directory_tenant_lookup", columns = {"spaceName", "tenantId", "deleted"})
@Index(name = "directory_name_lookup", columns = {"spaceName", "parent", "deleted", "directoryName"})
@Index(name = "directory_normalized_name_lookup",
        columns = {"spaceName", "parent", "deleted", "normalizedDirectoryName"})
@Index(name = "directory_renamed_loop", columns = "renamed")
public class SQLDirectory extends SQLEntity implements Directory, OptimisticCreate {

    @Part
    private static StorageUtils storageUtils;

    /**
     * Contains the tenant which owns this directory.
     */
    public static final Mapping TENANT_ID = Mapping.named("tenantId");
    @Length(64)
    private String tenantId;

    /**
     * Contains the space to which this directory belongs.
     */
    public static final Mapping SPACE_NAME = Mapping.named("spaceName");
    @Length(64)
    private String spaceName;

    /**
     * Contains the directory name.
     */
    public static final Mapping DIRECTORY_NAME = Mapping.named("directoryName");
    @Length(255)
    @NullAllowed
    private String directoryName;

    /**
     * Contains the directory name in lowercase.
     */
    public static final Mapping NORMALIZED_DIRECTORY_NAME = Mapping.named("normalizedDirectoryName");
    @Length(255)
    @LowerCase
    @NullAllowed
    private String normalizedDirectoryName;

    /**
     * Contains a reference to the parent directory.
     * <p>
     * Note that there must only be one directory per tenant which doesn't have a parent. This root directory
     * will be autocreated when needed.
     */
    public static final Mapping PARENT = Mapping.named("parent");
    @NullAllowed
    private final SQLEntityRef<SQLDirectory> parent =
            SQLEntityRef.on(SQLDirectory.class, BaseEntityRef.OnDelete.IGNORE).weak();

    /**
     * Stores if a directory has been fully initialized.
     * <p>
     * This is used by the optimistic locking algorithms to ensure that directory names remain unique
     * without requiring any locks.
     */
    public static final Mapping COMMITTED = Mapping.named("committed");
    private boolean committed;

    /**
     * Stores if the directory was marked as deleted.
     */
    public static final Mapping DELETED = Mapping.named("deleted");
    private boolean deleted;

    /**
     * Stores if the directory has been renamed.
     */
    public static final Mapping RENAMED = Mapping.named("renamed");
    private boolean renamed;

    @Part
    @Nullable
    private static BlobStorage layer2;

    @Transient
    private SQLBlobStorageSpace space;

    @Override
    public SQLBlobStorageSpace getStorageSpace() {
        if (space == null) {
            space = (SQLBlobStorageSpace) layer2.getSpace(spaceName);
        }
        return space;
    }

    @BeforeSave
    protected void beforeSave() {
        if (Strings.isFilled(directoryName)) {
            this.directoryName = storageUtils.sanitizePath(directoryName);
            if (Strings.isFilled(directoryName)) {
                this.normalizedDirectoryName = directoryName;
            } else {
                this.directoryName = null;
                this.normalizedDirectoryName = null;
            }
        }

        if (!isNew() && isChanged(DIRECTORY_NAME)) {
            renamed = true;
        }
    }

    protected SQLEntityRef<SQLDirectory> getParentRef() {
        return parent;
    }

    @Override
    public Directory getParent() {
        return getStorageSpace().fetchDirectoryParent(this);
    }

    @Override
    public boolean isRoot() {
        return parent.isEmpty();
    }

    @Override
    public String getName() {
        return getParentRef().isEmpty() ? spaceName : directoryName;
    }

    @Override
    public String getPath() {
        return getStorageSpace().determineDirectoryPath(this);
    }

    @Override
    public boolean hasChildNamed(String name) {
        return getStorageSpace().hasExistingChild(this, name, null, null);
    }

    @Override
    public boolean hasChildNamed(String name, @Nullable Blob exemptedBlob) {
        return getStorageSpace().hasExistingChild(this, name, null, exemptedBlob);
    }

    @Override
    public boolean hasChildNamed(String name, @Nullable Directory exemptedDirectory) {
        return getStorageSpace().hasExistingChild(this, name, exemptedDirectory, null);
    }

    @Override
    public Optional<? extends Directory> findChildDirectory(String name) {
        return getStorageSpace().findExistingChildDirectory(this, name);
    }

    @Override
    public Directory findOrCreateChildDirectory(String name) {
        return getStorageSpace().findOrCreateChildDirectory(this, name);
    }

    @Override
    public Optional<? extends Blob> findChildBlob(String name) {
        return getStorageSpace().findExistingChildBlob(this, name);
    }

    @Override
    public Blob findOrCreateChildBlob(String name) {
        return getStorageSpace().findOrCreateChildBlob(this, name);
    }

    @Override
    public void listChildDirectories(@Nullable String prefixFilter,
                                     int maxResults,
                                     Predicate<? super Directory> childProcessor) {
        getStorageSpace().listChildDirectories(this, prefixFilter, maxResults, childProcessor);
    }

    @Override
    public void listChildBlobs(@Nullable String prefixFilter,
                               @Nullable Set<String> fileTypes,
                               int maxResults,
                               Predicate<? super Blob> childProcessor) {
        getStorageSpace().listChildBlobs(this, prefixFilter, fileTypes, maxResults, childProcessor);
    }

    @Override
    public BasePageHelper<? extends Blob, ?, ?, ?> queryChildBlobsAsPage(WebContext webContext) {
        return getStorageSpace().queryChildBlobsAsPage(this, webContext);
    }

    @Override
    public void move(Directory newParent) {
        getStorageSpace().moveDirectory(this, (SQLDirectory) newParent);
    }

    @Override
    public void rename(String newName) {
        getStorageSpace().renameDirectory(this, newName);
    }

    @Override
    public void delete() {
        getStorageSpace().deleteDirectory(this);
    }

    @Override
    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    @Override
    public String getSpaceName() {
        return spaceName;
    }

    public void setSpaceName(String spaceName) {
        this.spaceName = spaceName;
    }

    public String getDirectoryName() {
        return directoryName;
    }

    public void setDirectoryName(String directoryName) {
        this.directoryName = directoryName;
    }

    @Override
    public boolean isCommitted() {
        return committed;
    }

    public void setCommitted(boolean committed) {
        this.committed = committed;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public boolean isRenamed() {
        return renamed;
    }
}
