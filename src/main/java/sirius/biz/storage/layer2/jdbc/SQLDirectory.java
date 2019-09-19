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
import sirius.db.jdbc.SQLEntity;
import sirius.db.jdbc.SQLEntityRef;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.kernel.commons.Limit;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Stores the metadata of a {@link Directory} in the underlying JDBC database.
 * <p>
 * Note that all non trivial methods delegate to the associated {@link SQLBlobStorageSpace}.
 */
@Framework(SQLBlobStorage.FRAMEWORK_JDBC_BLOB_STORAGE)
public class SQLDirectory extends SQLEntity implements Directory {

    public static final Mapping TENANT_ID = Mapping.named("tenantId");
    @Length(64)
    private String tenantId;

    @Transient
    private SQLBlobStorageSpace space;

    public static final Mapping SPACE_NAME = Mapping.named("spaceName");
    @Length(64)
    private String spaceName;

    public static final Mapping DIRECTORY_NAME = Mapping.named("directoryName");
    @Length(255)
    @NullAllowed
    private String directoryName;

    public static final Mapping PARENT = Mapping.named("parent");
    @NullAllowed
    private final SQLEntityRef<SQLDirectory> parent =
            SQLEntityRef.on(SQLDirectory.class, BaseEntityRef.OnDelete.REJECT);

    @Part
    private static BlobStorage layer2;

    @Override
    public SQLBlobStorageSpace getStorageSpace() {
        if (space == null) {
            space = (SQLBlobStorageSpace) layer2.getSpace(spaceName);
        }
        return space;
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
    public Optional<? extends Directory> findChildDirectory(String name) {
        return getStorageSpace().findChildDirectory(this, name);
    }

    @Override
    public Directory findOrCreateChildDirectory(String name) {
        return getStorageSpace().findOrCreateChildDirectory(this, name);
    }

    @Override
    public Optional<? extends Blob> findChildBlob(String name) {
        return getStorageSpace().findChildBlob(this, name);
    }

    @Override
    public Blob findOrCreateChildBlob(String name) {
        return getStorageSpace().findOrCreateChildBlob(this, name);
    }

    @Override
    public void listChildDirectories(@Nullable String prefixFilter,
                                     Limit limit,
                                     Function<? super Directory, Boolean> childProcessor) {
        getStorageSpace().listChildDirectories(this, prefixFilter, limit, childProcessor);
    }

    @Override
    public void listChildBlobs(@Nullable String prefixFilter,
                               @Nullable Set<String> fileTypes,
                               Limit limit,
                               Function<? super Blob, Boolean> childProcessor) {
        getStorageSpace().listChildBlobs(this, prefixFilter, fileTypes, limit, childProcessor);
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

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

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
}
