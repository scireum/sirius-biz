/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.packages;

import java.util.function.Consumer;

/**
 * Can be used to add permissions to the permissions returned by {@link PackageData#getPackageAndUpgradePermissions}.
 * <p>
 * If you provide additional permissions to the {@link sirius.web.security.UserInfo UserInfo} via an
 * {@link sirius.biz.tenants.AdditionalRolesProvider AdditionalRolesProvider}, you might need to add some of the
 * permissions here as well.
 */
public interface AdditionalPackagePermissionsProvider {

    /**
     * Adds additional roles to the given permissionConsumer, based on the given {@link PackageData} and parent.
     *
     * @param packageData        the {@link PackageData} instance this is called from
     * @param permissionConsumer the consumer for the additional permissions
     */
    void addAdditionalPermissions(PackageData packageData, Consumer<String> permissionConsumer);
}
