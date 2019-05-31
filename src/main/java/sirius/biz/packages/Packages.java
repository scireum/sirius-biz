/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.packages;

import com.typesafe.config.Config;
import sirius.biz.model.PermissionData;
import sirius.kernel.Sirius;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.kernel.settings.Extension;
import sirius.web.security.UserContext;

import java.util.List;

/**
 * This helper class is a wrapper for the <tt>security.packages</tt> configuratuions.
 */
@Register(classes = Packages.class)
public class Packages {

    /**
     * Returns the list of packages available for the given scope.
     *
     * @param scope the scope
     * @return the pricePackages of the scope
     */
    public List<String> getPackages(String scope) {
        return getScopeExtension(scope).getStringList("packages");
    }

    private Extension getScopeExtension(String scope) {
        return Sirius.getSettings().getExtension("security.packages", scope);
    }

    /**
     * Returns the translated name for the given package of the given scope.
     *
     * @param scope      the scope
     * @param packageKey the package key
     * @return the translated name for the given package of the given scope
     */
    public String getPackageName(String scope, String packageKey) {
        return NLS.get("Packages.package." + scope + "." + packageKey);
    }

    /**
     * Returns the list of upgrades available for the given scope.
     *
     * @param scope the scope
     * @return the upgrades of the scope
     */
    public List<String> getUpgrades(String scope) {
        return getScopeExtension(scope).getStringList("upgrades");
    }

    /**
     * Returns the translated name for the given upgrade of the given scope.
     *
     * @param scope      the scope
     * @param upgradeKey the upgrade key
     * @return the translated name for the given upgrade of the given scope
     */
    public String getUpgradeName(String scope, String upgradeKey) {
        return NLS.get("Packages.upgrade." + scope + "." + upgradeKey);
    }

    /**
     * Checks whether the current user has the required permission for a given role.
     *
     * @param role the role in question
     * @return true if the current user has the required permission, false if not
     */
    public boolean hasRequiredPermissionForRole(String role) {
        Config config = Sirius.getSettings().getConfig("security.packages.required-permissions-for-role");
        if (!config.hasPath(role)) {
            return true;
        }
        String requiredPermission = config.getString(role);
        return UserContext.get().getUser().hasPermission(requiredPermission);
    }

    /**
     * Checks whether the permissionData object has the required permission for a given role.
     *
     * @param role           the role in question
     * @param permissionData the permissionData object to check
     * @return true if the permissionData object has the required permission, false if not
     */
    public boolean hasRequiredPermissionForRole(String role, PermissionData permissionData) {
        Config config = Sirius.getSettings().getConfig("security.packages.required-permissions-for-role");
        if (!config.hasPath(role)) {
            return true;
        }
        String requiredPermission = config.getString(role);
        return permissionData.getPermissions().contains(requiredPermission);
    }
}
