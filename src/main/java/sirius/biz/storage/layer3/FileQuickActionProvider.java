/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.biz.tycho.QuickAction;
import sirius.kernel.di.std.AutoRegister;
import sirius.kernel.di.std.Priorized;

import java.util.function.Consumer;

/**
 * Represents a provider which provides link based quick actions which are associated with a {@linkplain VirtualFile file}.
 * <p>
 * A provider needs to wear a {@link sirius.kernel.di.std.Register} annotation in order to be discovered by the
 * framework.
 */
@AutoRegister
public interface FileQuickActionProvider extends Priorized {

    /**
     * Computes the quick action for the given file.
     *
     * @param virtualFile the file to compute the quick action for
     * @param consumer    the consumer to pass the quick action to
     */
    void computeQuickAction(VirtualFile virtualFile, Consumer<QuickAction> consumer);
}
