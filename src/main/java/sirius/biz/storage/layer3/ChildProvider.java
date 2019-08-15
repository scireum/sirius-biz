/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import java.util.Optional;

/**
 * Enumerates and resolves children for a {@link VirtualFile}.
 */
public interface ChildProvider {

    /**
     * Resolves the given name into a child file.
     *
     * @param parent the directory to resolve the child in
     * @param name   the name of the child to resolve
     * @return resolved file (which may or may not exist) or an empty optional if the given name cannot be resolved
     */
    Optional<VirtualFile> findChild(VirtualFile parent, String name);

    /**
     * Enumerates all children using the given search.
     *
     * @param parent the directory to enumerate
     * @param search the search criteria and result collector to use
     */
    void enumerate(VirtualFile parent, FileSearch search);
}
