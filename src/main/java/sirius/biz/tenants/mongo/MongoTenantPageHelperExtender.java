/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.packages.PackageData;
import sirius.biz.packages.Packages;
import sirius.biz.tenants.TenantController;
import sirius.biz.tenants.TenantData;
import sirius.biz.web.MongoPageHelper;
import sirius.biz.web.MongoPageHelperExtender;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.di.std.Register;

/**
 * Provides filter facets for the package data.
 * <p>
 * This is realized as extender so that other extenders can use a lower priority value to add filters above
 * the generated ones.
 */
@Register
public class MongoTenantPageHelperExtender implements MongoPageHelperExtender<MongoTenant> {

    @Part
    private Packages packages;

    @Override
    public void extend(MongoPageHelper<MongoTenant> pageHelper) {
        pageHelper.addTermAggregation(MongoTenant.TENANT_DATA.inner(TenantData.PACKAGE_DATA.inner(PackageData.PACKAGE_STRING)),
                                      name -> packages.getPackageName(TenantController.PACKAGE_SCOPE_TENANT, name));
        pageHelper.addTermAggregation(MongoTenant.TENANT_DATA.inner(TenantData.PACKAGE_DATA.inner(PackageData.UPGRADES)),
                                      name -> packages.getUpgradeName(TenantController.PACKAGE_SCOPE_TENANT, name));
    }

    @Override
    public Class<MongoTenant> getTargetType() {
        return MongoTenant.class;
    }

    @Override
    public int getPriority() {
        return Priorized.DEFAULT_PRIORITY;
    }
}
