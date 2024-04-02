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

    private static final ScriptableEventDispatcher NOOP_DISPATCHER = new ScriptableEventDispatcher() {

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public void handleEvent(ScriptableEvent event) {
            // do nothing
        }
    };

    @Part
    @Nullable
    private ScriptableEventDispatcherRepository dispatcherRepository;

    /**
     * Fetches the dispatcher for the current tenant.
     *
     * @param name the name of the dispatcher to fetch
     * @return the dispatcher for the current tenant with the given name or a NOOP dispatcher if no such dispatcher
     * exists. Note, if an empty <tt>name</tt> is given, the first available dispatcher for the current tenant is used.
     * This way, if exactly one dispatcher is present, it will be used in all import processes etc.
     */
    public ScriptableEventDispatcher fetchDispatcherForCurrentTenant(@Nullable String name) {
        if (dispatcherRepository == null) {
            return NOOP_DISPATCHER;
        }

        if (!ScopeInfo.DEFAULT_SCOPE.getScopeType().equals(UserContext.getCurrentScope().getScopeType())) {
            return NOOP_DISPATCHER;
        }

        UserInfo currentUser = UserContext.getCurrentUser();
        if (!currentUser.isLoggedIn()) {
            return NOOP_DISPATCHER;
        }

        return dispatcherRepository.fetchDispatcher(currentUser.getTenantId(), name).orElse(NOOP_DISPATCHER);
    }

    /**
     * Fetches all available dispatchers for the current tenant.
     *
     * @return a list of all available dispatchers for the current tenant
     */
    public List<String> fetchDispatchersForCurrentTenant() {
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

        return dispatcherRepository.fetchAvailableDispatchers(currentUser.getTenantId());
    }
}
