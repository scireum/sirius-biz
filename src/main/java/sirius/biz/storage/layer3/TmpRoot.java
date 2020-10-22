/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.biz.storage.layer2.Blob;
import sirius.biz.storage.layer2.BlobStorage;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.di.std.Register;
import sirius.web.security.ScopeInfo;
import sirius.web.security.UserContext;

import javax.annotation.Nullable;

/**
 * Provides a stealth directory to store temporary files.
 * <p>
 * Neither this directory itself nor its files are listed / enumerated in the VFS. However the directory as well
 * as each blob in the <b>tmp</b> {@link sirius.biz.storage.layer2.BlobStorageSpace} can be resolved and therefore
 * be accessed.
 * <p>
 * This will resolve paths like <tt>/tmp/BLOB-KEY/FILENAME</tt>. Note that the filename as well as the tenant which
 * tries to access the file must match. Most probably these blobs are created using
 * {@link sirius.biz.storage.layer2.BlobStorageSpace#createTemporaryBlob(String)} and then persisted once completely
 * written using {@link sirius.biz.storage.layer2.BlobStorageSpace#markAsUsed(Blob)}.
 * <p>
 * Note that these blobs will be deleted after a given period (usually 10 days).
 * <p>
 * One user of this framework is the {@link sirius.biz.jobs.JobsRoot} which accepts incoming files, stores them
 * in the <tt>tmp</tt> space and then uses the VFS path as noted above to pass these files on to the jobs framework.
 */
@Register
public class TmpRoot implements VFSRoot {

    /**
     * Contains the path prefix (root folder name) used in the VFS.
     */
    public static final String TMP_PATH = "tmp";

    /**
     * Contains the name of the temporary storage space in Layer 2.
     */
    public static final String TMP_SPACE = "tmp";

    @Part
    private BlobStorage blobStorage;

    @Override
    @Nullable
    public VirtualFile findChild(VirtualFile parent, String name) {
        if (!ScopeInfo.DEFAULT_SCOPE.equals(UserContext.getCurrentScope())) {
            return null;
        }

        if (TMP_PATH.equals(name)) {
            MutableVirtualFile result = new MutableVirtualFile(parent, name);
            result.markAsExistingDirectory();
            result.withChildren(new FindOnlyProvider(this::findTmpBlob));

            return result;
        }

        return null;
    }

    private VirtualFile findTmpBlob(VirtualFile parent, String name) {
        return blobStorage.getSpace(TMP_SPACE)
                          .findByBlobKey(name)
                          .filter(blob -> Strings.areEqual(UserContext.getCurrentUser().getTenantId(),
                                                           blob.getTenantId()))
                          .map(blob -> wrapBlob(parent, blob))
                          .orElse(null);
    }

    private VirtualFile wrapBlob(VirtualFile parent, Blob blob) {
        MutableVirtualFile result = new MutableVirtualFile(parent, blob.getBlobKey()).markAsExistingDirectory();
        result.withChildren(new FindOnlyProvider(this::unwrapBlob));
        result.attach(Blob.class, blob);

        return result;
    }

    private VirtualFile unwrapBlob(VirtualFile parent, String name) {
        Blob blob = parent.as(Blob.class);
        if (!Strings.areEqual(blob.getFilename(), name)) {
            return null;
        }

        MutableVirtualFile result = new MutableVirtualFile(parent, blob.getFilename());
        result.markAsExistingFile();
        result.withInputStreamSupplier(ignored -> blob.createInputStream());
        result.withFileHandleSupplier(ignored -> blob.download().orElse(null));

        return result;
    }

    @Override
    public void enumerate(VirtualFile parent, FileSearch search) {
        // We do not enumerate the "tmp" directory as it is hidden unless an exact path is given...
    }

    @Override
    public int getPriority() {
        return Priorized.DEFAULT_PRIORITY;
    }
}
