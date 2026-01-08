/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.biz.storage.layer3.VirtualFileSystemController;
import sirius.biz.web.BasePageHelper;
import sirius.kernel.health.HandledException;
import sirius.web.http.WebContext;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Provides the metadata for a directory within a {@link BlobStorageSpace}.
 */
public interface Directory {

    /**
     * Returns the storage space which is in charge of managing this object.
     *
     * @return the storage space in which this directory is stored
     */
    BlobStorageSpace getStorageSpace();

    /**
     * Returns the name of the {@link #getStorageSpace() storage space}.
     *
     * @return the name of the stroage space in which this directory resides.
     */
    String getSpaceName();

    /**
     * Returns the ID of the tenant for which this directory has been created.
     *
     * @return the id of the tenant which owns this directory
     */
    String getTenantId();

    /**
     * Returns the parent directory if this directory.
     *
     * @return the parent directory or <tt>null</tt> if this is the root directory
     */
    @Nullable
    Directory getParent();

    /**
     * Returns the unique ID of this directory.
     *
     * @return the unique id
     */
    String getIdAsString();

    /**
     * Returns the name of this directory.
     *
     * @return the name of this directory
     */
    String getName();

    /**
     * Returns the path of this directory.
     *
     * @return the path (including the name itself)
     */
    String getPath();

    /**
     * Determines if there is a child with the given name.
     *
     * @param name the name to check
     * @return <tt>true</tt> if either a directory or a blob with the given name exists as child
     */
    boolean hasChildNamed(String name);

    /**
     * Determines if there is a child with the given name, except for the given blob.
     *
     * @param name         the name to check
     * @param exemptedBlob the blob to ignore when searching for children
     * @return <tt>true</tt> if either a directory or a blob with the given name exists as child
     */
    boolean hasChildNamed(String name, @Nullable Blob exemptedBlob);

    /**
     * Determines if there is a child with the given name, except for the given directory.
     *
     * @param name              the name to check
     * @param exemptedDirectory the directory to ignore when searching for children
     * @return <tt>true</tt> if either a directory with the given name exists as child
     */
    boolean hasChildNamed(String name, @Nullable Directory exemptedDirectory);

    /**
     * Tries to find the sub directory with the given name.
     *
     * @param name the name of the sub directory to resolve
     * @return the resolved child or an empty optional if the directory doesn't exist
     */
    Optional<? extends Directory> findChildDirectory(String name);

    /**
     * Resolves or creates the sub directory with the given name.
     *
     * @param name the name of the sub directory to resolve
     * @return the resolved or newly created child directory
     */
    Directory findOrCreateChildDirectory(String name);

    /**
     * Tries to find the child blob with the given name.
     *
     * @param name the name of the child blob to resolve
     * @return the resolved child or an empty optional if the blob doesn't exist
     */
    Optional<? extends Blob> findChildBlob(String name);

    /**
     * Resolves or creates the blob with the given name.
     *
     * @param name the name of the blob to resolve
     * @return the resolved or newly created child blob
     */
    Blob findOrCreateChildBlob(String name);

    /**
     * Lists all child directories.
     *
     * @param prefixFilter   the prefix filer to apply on the name
     * @param maxResults     the maximal number of results to return or 0 to indicate that there is no upper limit
     * @param childProcessor the processor which is used to iterate over the result
     */
    void listChildDirectories(@Nullable String prefixFilter,
                              int maxResults,
                              Predicate<? super Directory> childProcessor);

    /**
     * Lists all child blobs.
     *
     * @param prefixFilter   the prefix filter to apply on the name
     * @param fileTypes      the list of accepted file types
     * @param maxResults     the maximal number of results to return or 0 to indicate that there is no upper limit
     * @param childProcessor the processor which is used to iterate over the result
     */
    void listChildBlobs(@Nullable String prefixFilter,
                        @Nullable Set<String> fileTypes,
                        int maxResults,
                        Predicate<? super Blob> childProcessor);

    /**
     * Executes a query for child blobs based on the filter settings given in the web context.
     * <p>
     * This is mainly used by the {@link L3Uplink} to implement a {@link sirius.biz.storage.layer3.ChildPageProvider}
     * for the {@link VirtualFileSystemController} in order to customize the view within "layer 2"
     * directories.
     *
     * @param webContext the context to draw parameters from
     * @return a helper to construct a {@link sirius.web.controller.Page} which contains the query result and
     * additional filter facets.
     */
    BasePageHelper<? extends Blob, ?, ?, ?> queryChildBlobsAsPage(WebContext webContext);

    /**
     * Deletes the directory along with all children.
     */
    void delete();

    /**
     * Moves this directory into a new directory.
     *
     * @param newParent the new directory to move the directory to.  Note that if <tt>null</tt> is passed in, a directory was
     *                  probably selected which was known to be incompatible - therefore the method is expected
     *                  to throw an appropriate exception.
     * @throws HandledException if the directory cannot be moved into the given directory (different space etc).
     */
    void move(@Nullable Directory newParent);

    /**
     * Renames this directory.
     *
     * @param newName the new name to use
     * @throws HandledException if the directory cannot be renamed
     */
    void rename(String newName);

    /**
     * Determines if this is the root directory of a space and tenant.
     *
     * @return <tt>true</tt> if this is a root directory, <tt>false</tt> otherwise
     */
    boolean isRoot();
}
