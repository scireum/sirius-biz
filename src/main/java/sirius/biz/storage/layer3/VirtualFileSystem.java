/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.PriorityParts;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;

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

    private VirtualFile root;

    @PriorityParts(VFSRoot.class)
    private List<VFSRoot> rootProviders;

    private class RootProvider implements ChildProvider {

        @Override
        public Optional<VirtualFile> findChild(VirtualFile parent, String name) {
            for (VFSRoot vfsRoot : rootProviders) {
                Optional<VirtualFile> result = vfsRoot.findChild(root(), name);
                if (result.isPresent()) {
                    return result;
                }
            }

            return Optional.empty();
        }

        @Override
        public void enumerate(@Nonnull VirtualFile parent, FileSearch search) {
            rootProviders.forEach(vfsRoot -> vfsRoot.enumerate(root(), search.forkChild()));
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
        return new MutableVirtualFile().withCanCreateChildren(MutableVirtualFile.CONSTANT_FALSE)
                                       .withChildren(new RootProvider())
                                       .withExistsFlagSupplier(MutableVirtualFile.CONSTANT_TRUE)
                                       .withDirectoryFlagSupplier(MutableVirtualFile.CONSTANT_TRUE);
    }

    /**
     * Resolves a path into a {@link VirtualFile}.
     * <p>
     * Note that the resolved file may not exist (yet).
     *
     * @param path the path to resolve. It has to start with a "/".
     * @return the resolved file or an empty optional if the path did contain invalid parts
     */
    public Optional<VirtualFile> tryResolve(String path) {
        if (Strings.isEmpty(path) || Strings.areEqual(path, "/")) {
            return Optional.of(root());
        }

        if (!path.startsWith("/")) {
            return Optional.empty();
        }

        return root().tryResolve(path.substring(1));
    }

    /**
     * Resolves a path into a {@link VirtualFile}.
     * <p>
     * Note that the resolved file may not exist (yet).
     *
     * @param path the path to resolve. It has to start with a "/".
     * @return the resolved file
     * @throws sirius.kernel.health.HandledException if the path cannot be resolved into a file
     */
    public VirtualFile resolve(String path) {
        return tryResolve(path).orElseThrow(() -> Exceptions.createHandled()
                                                            .withNLSKey("VirtualFileSystem.invalidPath")
                                                            .set("path", path)
                                                            .handle());
    }
}
