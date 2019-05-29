/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.packages;

import sirius.kernel.Sirius;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;

import java.util.List;

/**
 * This helper class is responsible for reading the conf
 */
@Register(classes = Packages.class)
public class Packages {

    /**
     * Returns the pricePackages for the given scope.
     *
     * @param scope the scope
     * @return the pricePackages of the scope
     */
    public List<String> getPricePackages(String scope) {
        return Sirius.getSettings().getExtension("security.packages", scope).getStringList("packages");
    }

    /**
     * Returns the translated name for the given package of the given scope.
     *
     * @param scope the scope
     * @param packageKey the package key
     * @return the translated name for the given package of the given scope
     */
    public String getPricePackageName(String scope, String packageKey) {
        return NLS.get("Packages.package." + scope + "." + packageKey);
    }

    /**
     * Returns the upgrades for the given scope.
     *
     * @param scope the scope
     * @return the upgrades of the scope
     */
    public List<String> getUpgrades(String scope) {
        return Sirius.getSettings().getExtension("security.packages", scope).getStringList("upgrades");
    }

    /**
     * Returns the translated name for the given upgrade of the given scope.
     *
     * @param scope the scope
     * @param upgradeKey the upgrade key
     * @return the translated name for the given upgrade of the given scope
     */
    public String getUpgradeName(String scope, String upgradeKey) {
        return NLS.get("Packages.upgrade." + scope + "." + upgradeKey);
    }
}
