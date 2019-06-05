/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.packages;

import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.web.http.WebContext;
import sirius.web.security.Permissions;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Contains the package, upgrades and additional and revoked permissions for an entity.
 */
public class PackageData extends Composite {

    @Part
    private static Packages packages;

    /**
     * The selected package
     */
    public static final Mapping PACKAGE_STRING = Mapping.named("packageString");
    @NullAllowed
    @Length(4096)
    private String packageString;

    /**
     * List of selected upgrades as string
     */
    public static final Mapping UPGRADES_STRING = Mapping.named("upgradesString");
    @NullAllowed
    @Length(4096)
    private String upgradesString;

    /**
     * List of the additionaly granted permissions as string
     */
    public static final Mapping ADDITIONAL_PERMISSIONS_STRING = Mapping.named("additionalPermissionsString");
    @NullAllowed
    @Length(4096)
    private String additionalPermissionsString;

    /**
     * List of the explicitly revoked permissions as string
     */
    public static final Mapping REVOKED_PERMISSIONS_STRING = Mapping.named("revokedPermissionsString");
    @NullAllowed
    @Length(4096)
    private String revokedPermissionsString;

    @Transient
    private Set<String> upgrades;

    @Transient
    private Set<String> additionalPermissions;

    @Transient
    private Set<String> revokedPermissions;

    @Transient
    private Set<String> expandedPermissions;

    public void setPackage(String packageString) {
        this.packageString = packageString;
    }

    public String getPackage() {
        return packageString;
    }

    /**
     * Returns all granted upgrades.
     * <p>
     * Note that this set can also be modified as the set will be written back to the database on save.
     *
     * @return the granted upgrades
     */
    public Set<String> getUpgrades() {
        if (upgrades == null) {
            upgrades = compileString(upgradesString);
        }
        return upgrades;
    }

    /**
     * Returns all granted additional permissions.
     * <p>
     * Note that this set can also be modified as the set will be written back to the database on save.
     *
     * @return the granted additional permissions
     */
    public Set<String> getAdditionalPermissions() {
        if (additionalPermissions == null) {
            additionalPermissions = compileString(additionalPermissionsString);
        }
        return additionalPermissions;
    }

    /**
     * Returns all revoked permissions.
     * <p>
     * Note that this set can also be modified as the set will be written back to the database on save.
     *
     * @return the revoked permissions
     */
    public Set<String> getRevokedPermissions() {
        if (revokedPermissions == null) {
            revokedPermissions = compileString(revokedPermissionsString);
        }
        return revokedPermissions;
    }

    private Set<String> compileString(String data) {
        Set<String> result = new TreeSet<>();
        if (Strings.isFilled(data)) {
            for (String permission : data.split(",")) {
                permission = permission.trim();
                if (Strings.isFilled(permission)) {
                    result.add(permission);
                }
            }
        }
        return result;
    }

    /**
     * Returns the permissions granted by the package and upgrades.
     *
     * @return the permissions granted by the package and upgrades
     */
    public Set<String> getPackageAndUpgradePermissions() {
        Set<String> packageAndUpgradeFeatures = new TreeSet<>();
        if (Strings.isFilled(getPackage())) {
            packageAndUpgradeFeatures.add(getPackage());
        }
        packageAndUpgradeFeatures.addAll(getUpgrades());
        return Permissions.applyProfiles(packageAndUpgradeFeatures);
    }

    /**
     * Returns every permission granted by the package, upgrades and additional permissions. Also takes the revoked
     * permissions into account.
     *
     * @return a set of every permission granted
     */
    public Set<String> getExpandedPermissions() {
        if (expandedPermissions == null) {
            expandedPermissions = new TreeSet<>();
            if (Strings.isFilled(getPackage())) {
                expandedPermissions.add(getPackage());
            }
            expandedPermissions.addAll(getUpgrades());
            expandedPermissions.addAll(getAdditionalPermissions());
            expandedPermissions = Permissions.applyProfiles(expandedPermissions);
            expandedPermissions.removeAll(getRevokedPermissions());
        }
        return Collections.unmodifiableSet(expandedPermissions);
    }

    /**
     * Checks if the permission is granted.
     * <p>
     * Will be checked against the {@link #getExpandedPermissions()}.
     *
     * @param permission the permission to check for
     * @return true, if this obejct has the permission, false otherwise
     */
    public boolean hasPermission(String permission) {
        Set<String> expanded = getExpandedPermissions();
        return Permissions.hasPermission(permission, expanded::contains);
    }

    /**
     * Combines the package, upgrades and additional permissions in a single set.
     *
     * @return the combined permissions
     */
    public Set<String> getCombinedPermissions() {
        Set<String> permissions = new TreeSet<>();
        if (Strings.isFilled(getPackage())) {
            permissions.add(getPackage());
        }
        permissions.addAll(getAdditionalPermissions());
        permissions.addAll(getUpgrades());
        return permissions;
    }

    /**
     * Load the package and upgrades from the web context.
     *
     * @param packagesScope the packages scope
     * @param context       the web context
     */
    public void loadPackageAndUpgradesFromContext(String packagesScope, WebContext context) {
        getUpgrades().clear();
        for (String upgrade : context.getParameters("upgrades")) {
            if (packages.getUpgrades(packagesScope).contains(upgrade)) {
                // ensure only real upgrades get in this list
                getUpgrades().add(upgrade);
            }
        }

        if (packages.getPackages(packagesScope).contains(context.get("package").asString())) {
            // ensure only real packages get in this field
            setPackage(context.get("package").asString());
        }
    }

    /**
     * Load the revoked and additional permissions from the web context.
     *
     * @param packagesScope  the packages scope
     * @param context        the web context
     * @param allPermissions all possible permissions
     */
    public void loadRevokedAndAdditionalPermissionsFromContext(String packagesScope,
                                                               WebContext context,
                                                               List<String> allPermissions) {
        getAdditionalPermissions().clear();
        getRevokedPermissions().clear();

        for (String permission : allPermissions) {
            switch (context.get(permission).asString()) {
                case "additional":
                    getAdditionalPermissions().add(permission);
                    break;
                case "revoked":
                    getRevokedPermissions().add(permission);
                    break;
                default:
            }
        }
    }

    @BeforeSave
    protected void updateStrings() {
        upgradesString = Strings.join(getUpgrades(), ",");
        additionalPermissionsString = Strings.join(getAdditionalPermissions(), ",");
        revokedPermissionsString = Strings.join(getRevokedPermissions(), ",");
    }
}
