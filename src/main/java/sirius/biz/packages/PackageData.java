/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.packages;

import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.Lob;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.OnValidate;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mixing.types.StringList;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.PartCollection;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Parts;
import sirius.web.security.Permissions;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Contains the package, upgrades and additional and revoked permissions for an entity.
 */
public class PackageData extends Composite {

    @Part
    private static Packages packages;

    @Parts(AdditionalPackagePermissionsProvider.class)
    private static PartCollection<AdditionalPackagePermissionsProvider> additionalPackagePermissionsProviders;

    @Transient
    private final BaseEntity<?> owner;

    /**
     * The selected package
     */
    public static final Mapping PACKAGE_STRING = Mapping.named("packageString");
    @NullAllowed
    @Length(50)
    private String packageString;

    /**
     * List of selected upgrades as string
     */
    public static final Mapping UPGRADES = Mapping.named("upgrades");
    @NullAllowed
    @Lob
    private final StringList upgrades = new StringList();

    /**
     * List of the additionally granted permissions as string
     */
    public static final Mapping ADDITIONAL_PERMISSIONS = Mapping.named("additionalPermissions");
    @NullAllowed
    @Lob
    private final StringList additionalPermissions = new StringList();

    /**
     * List of the explicitly revoked permissions as string
     */
    public static final Mapping REVOKED_PERMISSIONS = Mapping.named("revokedPermissions");
    @NullAllowed
    @Lob
    private final StringList revokedPermissions = new StringList();

    @Transient
    private Set<String> directPermissions;

    @Transient
    private final String scope;

    /**
     * Creates a new instance for the given owner.
     *
     * @param owner the owner entity which contains this composite.
     * @param scope the scope used to determine which packages and updgrades to consider
     */
    public PackageData(BaseEntity<?> owner, String scope) {
        this.owner = owner;
        this.scope = scope;
    }

    @OnValidate
    protected void validate(Consumer<String> warningConsumer) {
        if (Strings.isEmpty(packageString) && hasAvailablePackagesOrUpgrades()) {
            warningConsumer.accept("Es wurde noch kein Leistungspaket zugewiesen.");
        }
    }

    /**
     * Boilerplate method to determine all available packages for the scope of this package data.
     *
     * @return a list of all available packages
     */
    public List<String> getAvailablePackages() {
        return packages.getPackages(scope);
    }

    /**
     * Boilerplate method to determine all available upgrades for the scope of this package data.
     *
     * @return a list of all available upgrades
     */
    public List<String> getAvailableUpgrades() {
        return packages.getUpgrades(scope);
    }

    /**
     * Determines if there are either packages or upgrades defined for the scope of this package data.
     *
     * @return <tt>true</tt>if either {@link #getAvailablePackages()} or {@link #getAvailableUpgrades()} will return
     * a non-empty list
     */
    public boolean hasAvailablePackagesOrUpgrades() {
        return !getAvailablePackages().isEmpty() || !getAvailableUpgrades().isEmpty();
    }

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
    public StringList getUpgrades() {
        return upgrades;
    }

    /**
     * Returns all granted additional permissions.
     * <p>
     * Note that this set can also be modified as the set will be written back to the database on save.
     *
     * @return the granted additional permissions
     */
    public StringList getAdditionalPermissions() {
        return additionalPermissions;
    }

    /**
     * Returns all revoked permissions.
     * <p>
     * Note that this set can also be modified as the set will be written back to the database on save.
     *
     * @return the revoked permissions
     */
    public StringList getRevokedPermissions() {
        return revokedPermissions;
    }

    /**
     * Returns the permissions granted by the package and upgrades.
     *
     * @return the permissions granted by the package and upgrades
     */
    public Set<String> getPackageAndUpgradePermissions() {
        if (directPermissions == null) {
            Set<String> packageAndUpgradeFeatures = new HashSet<>();
            if (Strings.isFilled(getPackage())) {
                packageAndUpgradeFeatures.add(getPackage());
            }
            packageAndUpgradeFeatures.addAll(getUpgrades().data());

            for (AdditionalPackagePermissionsProvider permissionsProvider : additionalPackagePermissionsProviders) {
                permissionsProvider.addAdditionalPermissions(this, packageAndUpgradeFeatures::add);
            }

            Permissions.applyProfiles(packageAndUpgradeFeatures);
            directPermissions = packageAndUpgradeFeatures;
        }

        return Collections.unmodifiableSet(directPermissions);
    }

    /**
     * Combines the package, upgrades and additional permissions in a single set.
     *
     * @return the combined permissions
     */
    public Set<String> computeCombinedPermissions() {
        Set<String> permissions = new TreeSet<>();
        if (Strings.isFilled(getPackage())) {
            permissions.add(getPackage());
        }
        permissions.addAll(getUpgrades().data());
        permissions.addAll(getAdditionalPermissions().data());

        return permissions;
    }

    /**
     * Returns every permission granted by the package, upgrades and additional permissions. Also takes the revoked
     * permissions into account.
     *
     * @return a set of every permission granted
     */
    public Set<String> computeExpandedPermissions() {
        Set<String> permissions = new HashSet<>();
        if (Strings.isFilled(getPackage())) {
            permissions.add(getPackage());
        }
        permissions.addAll(getUpgrades().data());
        permissions.addAll(getAdditionalPermissions().data());

        Set<String> excludedPermissions = new HashSet<>(getRevokedPermissions().data());
        Permissions.applyProfiles(permissions, excludedPermissions);
        excludedPermissions.forEach(permissions::remove);

        return permissions;
    }

    /**
     * Load the package and upgrades (if existent).
     *
     * @param packagesScope the packages scope
     * @param upgrades      the list of upgrades to load
     * @param packageString the package to apply.
     */
    public void loadPackageAndUpgrades(String packagesScope, List<String> upgrades, String packageString) {
        List<String> accessibleUpgrades = packages.getUpgrades(packagesScope);
        packages.loadAccessiblePermissions(upgrades, accessibleUpgrades::contains, getUpgrades().modify());

        if (Strings.isEmpty(packageString) || packages.getPackages(packagesScope).contains(packageString)) {
            setPackage(packageString);
        }
    }

    /**
     * Load the revoked and additional permissions.
     *
     * @param allPermissions    all possible permissions
     * @param selectionFunction the function which determines if the permission is either additionally added, or revoked
     */
    public void loadRevokedAndAdditionalPermission(List<String> allPermissions,
                                                   UnaryOperator<String> selectionFunction) {
        getAdditionalPermissions().clear();
        getRevokedPermissions().clear();

        for (String permission : allPermissions) {
            String selection = selectionFunction.apply(permission);

            if ("additional".equals(selection)) {
                getAdditionalPermissions().add(permission);
            } else if ("revoked".equals(selection)) {
                getRevokedPermissions().add(permission);
            }
        }
    }

    public BaseEntity<?> getOwner() {
        return owner;
    }

    public String getScope() {
        return scope;
    }
}
