/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.kernel.di.std.AutoRegister;
import sirius.kernel.di.std.Priorized;

/**
 * Defines an interface which can be implemented to check if a file is in use.
 *
 * @see VirtualFileSystem#isInUse(VirtualFile)
 */
@AutoRegister
public interface DeletionCheckHandler extends Priorized {

    /**
     * Checks if the given virtual file is in use.
     * <p>
     * This can be implemented by product to check references of a file path in a database or similar.
     *
     * @param virtualFile the {@link VirtualFile} to check
     * @return <tt>true</tt> if the file is considered "in use"
     */
    boolean isInUse(VirtualFile virtualFile);

    @Override
    default int getPriority() {
        return Priorized.DEFAULT_PRIORITY;
    }
}
