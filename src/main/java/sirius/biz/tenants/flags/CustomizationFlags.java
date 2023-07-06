/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.flags;

import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.Tenants;
import sirius.biz.web.TenantAware;
import sirius.kernel.async.CallContext;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.settings.Settings;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Permits to disable or enable specific functions based on the current user, scope, browser setting etc.
 * <p>
 * This is mainly intended for migration projects where functionality needs either to be enabled for specific users
 * or scopes or needs to be "set back" to the old behavior. Therefore, a flag can be toggled in many places:
 * <ol>
 *     <li>By either having the string <tt>flag-FLAGNAME-disabled</tt> or <tt>flag-FLAGNAME</tt> in the user agent</li>
 *     <li>By setting it in the custom config in the current user or its tenant (in the block <tt>flags</tt>)</li>
 *     <li>By setting it in the scope config (in the block <tt>flags</tt>)</li>
 *     <li>By setting it in the tenant config which owns the current scope (in the block <tt>flags</tt>)</li>
 *     <li>By setting it in the config of the system tenant (in the block <tt>flags.global</tt>)</li>
 * </ol>
 * <p>
 * These places are checked in order and the first given value is used. Therefore, especially via the system tenant,
 * a whole system can be set to either "all on" or "all off" - with specific exceptions on multiple levels.
 * <p>
 * Note that the API should be mainly accessed via {@link CustomizationFlag}.
 */
@Register(classes = CustomizationFlags.class)
public class CustomizationFlags {

    private static final List<String> knownFlags = new ArrayList<>();

    @Part
    private Tenants<?, ?, ?> tenants;

    protected static synchronized void addKnownFlag(String name) {
        knownFlags.add(name);
    }

    /**
     * Reports a list of all statically known flags.
     *
     * @return a list fo all flags which are created via {@link CustomizationFlag}.
     */
    public synchronized List<String> getKnownFlags() {
        return new ArrayList<>(knownFlags);
    }

    /**
     * Checks if the given flag is enabled or disabled and also reports the source of the value.
     * <p>
     * Note that this is rather a debugging / reporting API and the main access should be performed via
     * {@link CustomizationFlag#isEnabled()} or {@link CustomizationFlag#isEnabled(boolean)}. This way, all
     * flags which are in use, get properly reported via {@link #getKnownFlags()}.
     *
     * @param flagName     the name of the flag to query
     * @param defaultValue the default to use in case no config is present anywhere
     * @return the value of the flag and the source where it was found
     */
    public Tuple<Boolean, String> isFlagEnabled(String flagName, boolean defaultValue) {

        return readFromUserAgent(flagName).or(() -> readFromUserSettings(flagName))
                                          .or(() -> readFromScopeSettings(flagName))
                                          .or(() -> readFromScopeTenantSettings(flagName))
                                          .or(() -> readFromSystemTenantSettings(flagName))
                                          .orElseGet(() -> Tuple.create(defaultValue, "default"));
    }

    private Optional<Tuple<Boolean, String>> readFromUserAgent(String flagName) {
        WebContext webContext = CallContext.getCurrent().get(WebContext.class);
        if (webContext.isValid()) {
            if (webContext.getUserAgent().contains("flag-" + flagName + "-disabled")) {
                return Optional.of(Tuple.create(false, "user-agent"));
            } else if (webContext.getUserAgent().contains("flag-" + flagName)) {
                return Optional.of(Tuple.create(true, "user-agent"));
            }
        }

        return Optional.empty();
    }

    private Optional<Tuple<Boolean, String>> readFromScopeTenantSettings(String flagName) {
        if (tenants == null) {
            return Optional.empty();
        }

        Object scopeObject = UserContext.getCurrentScope().getScopeObject(Object.class);
        if (scopeObject instanceof TenantAware tenantAwareScope) {
            return tenants.fetchCachedTenant(tenantAwareScope.getTenant().getIdAsString())
                          .flatMap(tenant -> readFromSettings(tenant.getSettings(), flagName, "scope-tenant-settings"));
        } else {
            return Optional.empty();
        }
    }

    private Optional<Tuple<Boolean, String>> readFromSystemTenantSettings(String flagName) {
        if (tenants == null) {
            return Optional.empty();
        }
        Tenant<?> systemTenant = tenants.fetchCachedTenant(tenants.getSystemTenantId()).orElse(null);
        if (systemTenant == null) {
            return Optional.empty();
        }
        Settings settings = systemTenant.getSettings();
        if (settings.has("flags.global." + flagName)) {
            return Optional.of(Tuple.create(settings.get("flags.global." + flagName).asBoolean(),
                                            "system-tenant-settings"));
        } else {
            return Optional.empty();
        }
    }

    private Optional<Tuple<Boolean, String>> readFromScopeSettings(String flagName) {
        return readFromSettings(UserContext.getCurrentScope().getSettings(), flagName, "scope-settings");
    }

    private Optional<Tuple<Boolean, String>> readFromUserSettings(String flagName) {
        return readFromSettings(UserContext.getCurrentUser().getSettings(), flagName, "user-settings");
    }

    private Optional<Tuple<Boolean, String>> readFromSettings(Settings settings, String flagName, String source) {
        if (settings.has("flags." + flagName)) {
            return Optional.of(Tuple.create(settings.get("flags." + flagName).asBoolean(), source));
        } else {
            return Optional.empty();
        }
    }
}
