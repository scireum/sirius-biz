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

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * This helper class is a wrapper for the <tt>security.packages</tt> configurations.
 * <p>
 * For a high-level overview see the README.md of this package.
 */
@Register(classes = Packages.class)
public class Packages {

    /**
     * Returns the list of packages available for the given scope.
     *
     * @param scope the scope
     * @return the pricePackages of the scope
     */
    public List<String> getPackages(@Nonnull String scope) {
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
    public String getPackageName(@Nonnull String scope, @Nonnull String packageKey) {
        return NLS.get("Packages.package." + scope + "." + packageKey);
    }

    /**
     * Returns the translated description for the given package of the given scope.
     *
     * @param scope      the scope
     * @param packageKey the package key
     * @return the translated name for the given package of the given scope
     */
    public String getPackageDescription(@Nonnull String scope, @Nonnull String packageKey) {
        return NLS.getIfExists("Packages.package." + scope + "." + packageKey + ".description", null).orElse("");
    }

    /**
     * Returns the list of upgrades available for the given scope.
     *
     * @param scope the scope
     * @return the upgrades of the scope
     */
    public List<String> getUpgrades(@Nonnull String scope) {
        return getScopeExtension(scope).getStringList("upgrades");
    }

    /**
     * Returns the translated name for the given upgrade of the given scope.
     *
     * @param scope      the scope
     * @param upgradeKey the upgrade key
     * @return the translated name for the given upgrade of the given scope
     */
    public String getUpgradeName(@Nonnull String scope, @Nonnull String upgradeKey) {
        return NLS.get("Packages.upgrade." + scope + "." + upgradeKey);
    }

    /**
     * Returns the translated description for the given upgrade of the given scope.
     *
     * @param scope      the scope
     * @param upgradeKey the upgrade key
     * @return the translated name for the given upgrade of the given scope
     */
    public String getUpgradeDescription(@Nonnull String scope, @Nonnull String upgradeKey) {
        return NLS.getIfExists("Packages.upgrade." + scope + "." + upgradeKey + ".description", null).orElse("");
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

    /**
     * Filters the given list of <tt>allPermissions</tt> to only contain accessible ones.
     *
     * <p>
     * This utilizes the same mapping as {@link #hasRequiredPermissionForPermission(String, Predicate)}
     *
     * @param allPermissions         the list of permissions to process
     * @param hasPermissionPredicate the predicate which decides if a given permission is present
     * @return a list of all accessible permissions (in <tt>allPermissions</tt>)
     */
    public List<String> filterAccessiblePermissions(List<String> allPermissions,
                                                    Predicate<String> hasPermissionPredicate) {
        Config config = Sirius.getSettings().getConfig("security.packages.required-permissions-for-permission");
        if (config.isEmpty()) {
            return allPermissions;
        }

        return allPermissions.stream()
                             .filter(permission -> !config.hasPath(permission)
                                                   || Permissions.hasPermission(config.getString(permission),
                                                                                hasPermissionPredicate))
                             .toList();
    }

    /**
     * Loads a list of assigned permissions into the given destination.
     * <p>
     * Filters the list of permissions using the <tt>isAccessible</tt> predicate.
     * <p>
     * This can be used to fill the {@link PermissionData#getPermissions()} to only contain valid
     * and accessible roles / permissions.
     *
     * @param assignedPermissions the list of permissions to assign
     * @param isAccessible        the predicate used to determine if a given permission is accessible or not
     * @param destination         the destination which will be first cleared and then supplied with the accessible
     *                            permissions
     */
    public void loadAccessiblePermissions(Collection<String> assignedPermissions,
                                          Predicate<String> isAccessible,
                                          Collection<String> destination) {
        destination.clear();
        assignedPermissions.stream().filter(isAccessible).forEach(destination::add);
    }
}
