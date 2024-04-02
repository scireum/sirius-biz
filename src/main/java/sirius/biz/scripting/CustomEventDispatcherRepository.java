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
import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Provides a repository which stores and manages {@link CustomEventDispatcher custom event dispatchers}.
 * <p>
 * Note that the repository isn't usually accessed directly. Instead, the {@link CustomEvents} class should be used.
 */
@AutoRegister
public interface CustomEventDispatcherRepository {

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
     * no such dispatcher exists. <b>NOTE:</b> if an empty name is given, the first dispatcher for the given tenant
     * is used. This helps to simplify the usage of custom events.
     */
    Optional<CustomEventDispatcher> fetchDispatcher(@Nonnull String tenantId, @Nullable String name);
}
