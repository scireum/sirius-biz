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
import sirius.web.security.Permissions;
import sirius.web.security.UserContext;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * This helper class is a wrapper for the <tt>security.packages</tt> configuratuions.
 * <p>
 * For a highlevel overview see the README.md of this package.
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
     * Checks whether the current user has the required permission for a given permission.
     *
     * @param permission the permission in question
     * @return true if the current user has the required permission, false if not
     */
    public boolean hasRequiredPermissionForPermission(String permission) {
        Set<String> permissions = UserContext.get().getUser().getPermissions();
        return hasRequiredPermissionForPermission(permission, permissions::contains);
    }

    /**
     * Checks whether the permissionData object has the required permission for a given permission.
     *
     * @param permission     the permission in question
     * @param permissionData the permissionData object to check
     * @return true if the permissionData object has the required permission, false if not
     */
    public boolean hasRequiredPermissionForPermission(String permission, PermissionData permissionData) {
        return hasRequiredPermissionForPermission(permission,
                                                  requiredPermission -> permissionData.getPermissions()
                                                                                      .contains(requiredPermission));
    }

    /**
     * Checks whether some object has the required permission for a given permission, via a predicate.
     * <p>
     * The predicate will be called with the required permission for the permission and should return true or false,
     * depending on if the required permission is present.
     *
     * @param permission             the permission in question
     * @param hasPermissionPredicate a predicate which will determine if the required permission is present
     * @return true if the object has the required permission (determined via the predicate), false if not
     */
    public boolean hasRequiredPermissionForPermission(String permission, Predicate<String> hasPermissionPredicate) {
        Config config = Sirius.getSettings().getConfig("security.packages.required-permissions-for-permission");
        if (!config.hasPath(permission)) {
            return true;
        }
        String requiredPermission = config.getString(permission);
        return Permissions.hasPermission(requiredPermission, hasPermissionPredicate);
    }
}
