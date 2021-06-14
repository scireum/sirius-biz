/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.PriorityParts;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Provides the main entrypoint into the <b>Virtual File System</b>.
 * <p>
 * This serves mainly three purposes:
 * <ol>
 * <li>Making internally stored files accessible to the outside by using <b>downlinks</b> like FTP, SFTP or SCP</li>
 * <li>Making external files accessible to the software by using <b>uplinks</b> like FS, CIFS, SFTP</li>
 * <li>Provide a uniform management UI for all file based operations within sirius</li>
 * </ol>
 */
@Register(classes = VirtualFileSystem.class)
public class VirtualFileSystem {

    /**
     * Defines the name of the sub scope used by the {@link sirius.biz.storage.layer3.downlink.ftp.FTPServer} and
     * {@link sirius.biz.storage.layer3.downlink.ssh.SSHServer} which grants access per FTP, SFTP or SCP.
     */
    public static final String SUB_SCOPE_VFS = "vfs";

    @Part
    private StorageUtils utils;

    private VirtualFile root;

    @PriorityParts(VFSRoot.class)
    private List<VFSRoot> rootProviders;

    /**
     * Inline implementation which delegates all calls to the collected <tt>rootProviders</tt>.
     */
    private class RootProvider implements ChildProvider {

        @Override
        @Nullable
        public VirtualFile findChild(VirtualFile parent, String name) {
            for (VFSRoot vfsRoot : rootProviders) {
                VirtualFile result = vfsRoot.findChild(root(), name);
                if (result != null) {
                    return result;
                }
            }

            return null;
        }

        @Override
        public void enumerate(@Nonnull VirtualFile parent, FileSearch search) {
            rootProviders.forEach(vfsRoot -> vfsRoot.enumerate(root(), search));
        }
    }

    /**
     * Returns the root directory (<tt>/</tt>) wrapped as virtual file.
     *
     * @return the root directory of the virtual file system
     */
    public VirtualFile root() {
        if (root == null) {
            root = makeRoot();
        }

        return root;
    }

    private VirtualFile makeRoot() {
        return new MutableVirtualFile().markAsExistingDirectory()
                                       .withCanCreateChildren(MutableVirtualFile.CONSTANT_FALSE)
                                       .withChildren(new RootProvider());
    }

    /**
     * Resolves a path into a {@link VirtualFile}.
     * <p>
     * Note that the resolved file may not exist (yet).
     *
     * @param path the path to resolve.
     * @return the resolved file
     */
    @Nonnull
    public VirtualFile resolve(String path) {
        String sanitizedPath = utils.sanitizePath(path);

        if (Strings.isEmpty(sanitizedPath)) {
            return root();
        }

        return root().resolve(sanitizedPath);
    }

    /**
     * Builds a path from the given parts.
     * <p>
     * Builds a path like <tt>/foo/bar/baz</tt> for <tt>[foo, bar, baz]</tt>.
     *
     * @param parts the individual folder / file names to concatenate to a path
     * @return the absolute path built from the given parts
     */
    public String makePath(String... parts) {
        // Note that this is currently a very simple implementation but might be enhanced with additional
        // checks or cleanups...
        return "/" + Strings.join("/", parts);
    }
}
