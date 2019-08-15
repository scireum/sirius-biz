/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.uplink;

import sirius.biz.storage.layer3.ChildProvider;
import sirius.biz.storage.layer3.FileSearch;
import sirius.biz.storage.layer3.MutableVirtualFile;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.kernel.commons.Strings;
import sirius.kernel.settings.Extension;
import sirius.web.security.ScopeInfo;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Represents an uplink which has been defined in the system config.
 * <p>
 * All uplinks are collected by the {@link ConfigBasedUplinksRoot}.
 */
public abstract class ConfigBasedUplink {

    protected String description;
    protected String permission;
    protected String name;
    protected Extension config;
    protected boolean readonly;
    protected VirtualFile file;

    protected ChildProvider innerChildProvider = new ChildProvider() {
        @Override
        public Optional<VirtualFile> findChild(VirtualFile directory, String name) {
            return findChildInDirectory(directory, name);
        }

        @Override
        public void enumerate(VirtualFile parent, FileSearch search) {
            enumerateDirectoryChildren(parent, search);
        }
    };

    /**
     * Creates a new instance based on the config section.
     *
     * @param config the configuration of this uplink
     */
    protected ConfigBasedUplink(Extension config) {
        this.readonly = config.get("readonly").asBoolean();
        this.description = config.get("description").asString();
        this.permission = config.get("permission").asString();
        this.name = config.get("name").asString(config.getId());
        this.config = config;
    }

    /**
     * Creates the root directory of this uplink.
     *
     * @param parent the parent to use
     * @return the root directory of this uplink
     */
    public VirtualFile getFile(VirtualFile parent) {
        if (file == null) {
            file = makeDirectory(parent);
        }
        return file;
    }

    /**
     * Determines if the current user may access this uplink.
     *
     * @return <tt>true</tt> if all checks were successful, <tt>false</tt> otherwise
     */
    public boolean checkPermission() {
        if (!Strings.areEqual(ScopeInfo.DEFAULT_SCOPE.getScopeId(), UserContext.getCurrentScope().getScopeId())) {
            return false;
        }

        UserInfo currentUser = UserContext.getCurrentUser();
        if (!currentUser.isLoggedIn()) {
            return false;
        }

        return currentUser.hasPermission(permission);
    }

    /**
     * Returns the effective directory name to use.
     *
     * @return the directory name of the virtual root directory
     */
    @Nonnull
    protected String getDirectoryName() {
        return name;
    }

    /**
     * Creates the virtual root directory.
     *
     * @param parent the parent to use
     * @return the virtual root directory
     */
    protected VirtualFile makeDirectory(@Nonnull VirtualFile parent) {
        return createDirectoryFile(parent).withDescription(description)
                                          .markAsExistingDirectory()
                                          .withChildren(innerChildProvider);
    }

    /**
     * Instantiates the virtual root directory.
     *
     * @param parent the parent to use
     * @return the virtual root directory
     */
    protected MutableVirtualFile createDirectoryFile(@Nonnull VirtualFile parent) {
        return new MutableVirtualFile(parent, getDirectoryName());
    }

    /**
     * Resolves the child with the given name in the given parent directory.
     *
     * @param parent the parent directory
     * @param name   the name of the child
     * @return the resolved child (existing or not) or an empty optional if the child cannot be resolved
     */
    protected abstract Optional<VirtualFile> findChildInDirectory(VirtualFile parent, String name);

    /**
     * Enumerates all children in the given parent matching the given search.
     *
     * @param parent the parent directory
     * @param search the search which defines all filters and collects results
     */
    protected abstract void enumerateDirectoryChildren(VirtualFile parent, FileSearch search);
}
