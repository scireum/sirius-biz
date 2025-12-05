/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.tycho.search.OpenSearchProvider;
import sirius.biz.tycho.search.OpenSearchResult;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.query.Query;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.Formatter;
import sirius.kernel.nls.NLS;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.function.Consumer;

/**
 * Provides the base implementation to make implementations of {@link Tenant} visible in the
 * {@link sirius.biz.tycho.search.OpenSearchController}.
 *
 * @param <I> the type of database IDs used by the concrete implementation
 * @param <T> specifies the effective entity type used to represent Tenants
 */
public abstract class TenantSearchProvider<I extends Serializable, T extends BaseEntity<I> & Tenant<I>>
        implements OpenSearchProvider {

    @Part
    private Tenants<I, T, ?> tenants;

    @Override
    public String getLabel() {
        return NLS.get("Tenant.plural");
    }

    @Nullable
    @Override
    public String getUrl() {
        return "/tenants";
    }

    @Override
    public String getIcon() {
        return "fa-industry";
    }

    @Override
    public boolean ensureAccess() {
        UserInfo currentUser = UserContext.getCurrentUser();
        return currentUser.hasPermission(TenantController.PERMISSION_MANAGE_TENANTS);
    }

    @Override
    public void query(String query, int maxResults, Consumer<OpenSearchResult> resultCollector) {
        UserInfo currentUser = UserContext.getCurrentUser();

        Query<?, T, ?> tenantQuery = createBaseQuery(query);
        tenantQuery.limit(maxResults);

        tenantQuery.iterateAll(tenant -> {
            OpenSearchResult openSearchResult = new OpenSearchResult().withLabel(tenant.getTenantData().getName());
            openSearchResult.withDescription(Formatter.create("[${zip}][ ${city}]")
                                                      .set("zip", tenant.getTenantData().getAddress().getZip())
                                                      .set("city", tenant.getTenantData().getAddress().getCity())
                                                      .smartFormat());
            openSearchResult.withURL("/tenant/" + tenant.getIdAsString());

            if (currentUser.hasPermission(TenantUserManager.PERMISSION_SELECT_TENANT)) {
                openSearchResult.withTemplateFromCode("""
                                                              <i:arg name="tenant" type="sirius.biz.tenants.Tenant"/>
                                                              @tenant.getTenantData().getAddress().getZip() @tenant.getTenantData().getAddress().getCity()
                                                              <br>
                                                              <a href="/tenants/select/@tenant.getIdAsString()" class="card-link">@i18n("TenantController.select")</a>
                                                              """, tenant);
            }
            resultCollector.accept(openSearchResult);
        });
    }

    protected abstract Query<?, T, ?> createBaseQuery(String query);

    @Override
    public int getPriority() {
        return 100;
    }
}
