/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.batch.file.EntityExportJobFactory;
import sirius.biz.jobs.params.MultiSelectStringParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.params.SelectStringParameter;
import sirius.biz.packages.Packages;
import sirius.biz.process.ProcessContext;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.query.Query;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;
import sirius.web.http.QueryString;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provides a base class for job factories which export {@linkplain Tenant tenants}.
 *
 * @param <E> the type of tenants being exported
 * @param <Q> the query type used to select tenants
 */
public abstract class TenantExportJobFactory<E extends BaseEntity<?> & Tenant<?>, Q extends Query<Q, E, ?>>
        extends EntityExportJobFactory<E, Q> {

    private static final String PACKAGE_SCOPE = TenantController.PACKAGE_SCOPE_TENANT;

    @Part
    private static Packages packagesHelper;

    @ConfigValue("security.tenantPermissions")
    private static List<String> permissions;

    protected final Parameter<String> packageParameter = new SelectStringParameter("package",
                                                                                   "$PackageData.packageString").withEntriesProvider(
            () -> packagesHelper.getPackages(PACKAGE_SCOPE)
                                .stream()
                                .collect(Collectors.toMap(Function.identity(),
                                                          value -> packagesHelper.getPackageName(PACKAGE_SCOPE, value),
                                                          (object, ignored) -> object,
                                                          LinkedHashMap::new))).build();

    protected final Parameter<List<String>> upgradesParameter = new MultiSelectStringParameter("upgrades",
                                                                                               "$PackageData.upgrades").withEntriesProvider(
            () -> packagesHelper.getUpgrades(PACKAGE_SCOPE)
                                .stream()
                                .collect(Collectors.toMap(Function.identity(),
                                                          value -> packagesHelper.getUpgradeName(PACKAGE_SCOPE, value),
                                                          (object, ignored) -> object,
                                                          LinkedHashMap::new))).build();

    protected final Parameter<List<String>> permissionsParameter =
            new MultiSelectStringParameter("permissions", "$Tenant.permissions").withEntriesProvider(() -> {
                return permissions.stream()
                                  .collect(Collectors.toMap(Function.identity(),
                                                            permission -> NLS.get("Permission." + permission),
                                                            (object, ignored) -> object,
                                                            LinkedHashMap::new));
            }).build();

    @Override
    public int getPriority() {
        return 9200;
    }

    @Override
    public String getCategory() {
        return StandardCategories.USERS_AND_TENANTS;
    }

    @Override
    protected boolean hasPresetFor(QueryString queryString, Object targetObject) {
        return queryString.path().startsWith("/tenant");
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        super.collectParameters(parameterCollector);
        parameterCollector.accept(packageParameter);
        parameterCollector.accept(upgradesParameter);
        parameterCollector.accept(permissionsParameter);
    }

    @Override
    protected boolean includeEntityDuringExport(E entity, ProcessContext processContext) {
        return processContext.getParameter(permissionsParameter)
                             .map(necessaryPermissions -> entity.getPermissions().containsAll(necessaryPermissions))
                             .orElse(true);
    }
}
