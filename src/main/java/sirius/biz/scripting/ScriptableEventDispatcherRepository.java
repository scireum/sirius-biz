/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.scripting;

import sirius.kernel.di.std.AutoRegister;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Provides a repository which stores and manages {@link ScriptableEventDispatcher custom event dispatchers}.
 * <p>
 * Note that the repository isn't usually accessed directly. Instead, the {@link ScriptableEvents} class should be used.
 */
@AutoRegister
public interface ScriptableEventDispatcherRepository {

    /**
     * Fetches the dispatchers for the given tenant.
     * <p>
     * Note that this will include dispatchers defined in any parent tenant.
     *
     * @param tenantId the tenant for which to fetch the dispatcher
     * @return a list of dispatchers for the given tenant, or an empty list if no dispatchers are available
     */
    List<ScriptableEventDispatcher> fetchDispatchers(@Nonnull String tenantId);
}
