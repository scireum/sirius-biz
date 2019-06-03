/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.packages;

import sirius.biz.web.Autoloaded;
import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.kernel.commons.Strings;

import java.util.Set;
import java.util.TreeSet;

/**
 * Contains the package, upgrades and additional and revoked permissions for an entity.
 */
public class PackageData extends Composite {

    public static final Mapping PACKAGE_STRING = Mapping.named("packageString");
    @Autoloaded
    @NullAllowed
    private String packageString;

    public static final Mapping UPGRADES_STRING = Mapping.named("upgradesString");
    @Autoloaded
    @NullAllowed
    private String upgradesString;

    public static final Mapping ADDITIONAL_PERMISSIONS_STRING = Mapping.named("additionalPermissionsString");
    @NullAllowed
    private String additionalPermissionsString;

    public static final Mapping REVOKED_PERMISSIONS_STRING = Mapping.named("revokedPermissionsString");
    @NullAllowed
    private String revokedPermissionsString;

    @Transient
    private Set<String> upgrades;

    @Transient
    private Set<String> additionalPermissions;

    @Transient
    private Set<String> revokedPermissions;

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

    @BeforeSave
    protected void updateStrings() {
        upgradesString = Strings.join(getUpgrades(), ",");
        additionalPermissionsString = Strings.join(getAdditionalPermissions(), ",");
        revokedPermissionsString = Strings.join(getRevokedPermissions(), ",");
    }
}
