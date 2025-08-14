/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.scripting;

import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.ScopeInfo;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Provides access to tenant specific custom event dispatchers.
 * <p>
 * Event dispatchers are stored and managed by a {@link ScriptableEventDispatcherRepository}. Commonly these are
 * defined via scripts which modify a {@link ScriptableEventRegistry} and are then transformed into a
 * {@link ScriptableEventDispatcher} with the help of a {@link SimpleScriptableEventDispatcher}.
 */
@Register(classes = ScriptableEvents.class)
public class ScriptableEvents {

    @Part
    @Nullable
    private ScriptableEventDispatcherRepository dispatcherRepository;

    /**
     * Fetches the dispatchers for the current tenant.
     *
     * @return the dispatchers for the current tenant or an empty list if no dispatchers are available
     */
    public List<ScriptableEventDispatcher> fetchDispatcherForCurrentTenant() {
        if (dispatcherRepository == null) {
            return Collections.emptyList();
        }

        if (!ScopeInfo.DEFAULT_SCOPE.getScopeType().equals(UserContext.getCurrentScope().getScopeType())) {
            return Collections.emptyList();
        }

        UserInfo currentUser = UserContext.getCurrentUser();
        if (!currentUser.isLoggedIn()) {
            return Collections.emptyList();
        }

        return dispatcherRepository.fetchDispatchers(currentUser.getTenantId());
    }
}
