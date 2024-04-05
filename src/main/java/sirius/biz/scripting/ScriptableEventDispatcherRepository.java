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
import java.util.Optional;

/**
 * Provides a repository which stores and manages {@link ScriptableEventDispatcher custom event dispatchers}.
 * <p>
 * Note that the repository isn't usually accessed directly. Instead, the {@link ScriptableEvents} class should be used.
 */
@AutoRegister
public interface ScriptableEventDispatcherRepository {

    /**
     * Fetches all available dispatchers for the given tenant.
     *
     * @param tenantId the tenant for which to fetch the dispatchers
     * @return a list of all available dispatchers for the given tenant
     */
    List<String> fetchAvailableDispatchers(@Nonnull String tenantId);

    /**
     * Fetches the dispatcher with the given name for the given tenant.
     *
     * @param tenantId the tenant for which to fetch the dispatcher
     * @param name     the name of the dispatcher to fetch
     * @return the dispatcher with the given name for the given tenant wrapped as optional or an empty optional if
     * no such dispatcher exists.
     */
    Optional<ScriptableEventDispatcher> fetchDispatcher(@Nonnull String tenantId, @Nonnull String name);
}
