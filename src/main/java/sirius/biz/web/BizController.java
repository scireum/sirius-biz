/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.biz.model.BizEntity;
import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.TenantAware;
import sirius.biz.tenants.Tenants;
import sirius.biz.tenants.UserAccount;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.db.mixing.Column;
import sirius.db.mixing.Entity;
import sirius.db.mixing.OMA;
import sirius.db.mixing.Property;
import sirius.web.controller.BasicController;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by aha on 08.05.15.
 */
public class BizController extends BasicController {

    @Part
    protected OMA oma;

    @Part
    protected Tenants tenants;

    public static final Log LOG = Log.get("biz");

    protected void assertTenant(TenantAware tenantAware) {

    }

    protected void assertNotNew(Entity obj) {
        assertNotNull(obj);
        if (obj.isNew()) {
            //TODO
        }
    }

    @ConfigValue("product.baseUrl")
    private String baseUrl;

    protected String getBaseUrl() {
        return baseUrl;
    }

    protected void load(WebContext ctx, Entity entity) {
        for (Property property : entity.getDescriptor().getProperties()) {
            if (isAutoloaded(property)) {
                if (ctx.hasParameter(property.getName())) {
                    property.parseValue(entity, ctx.get(property.getName()));
                }
            }
        }
    }

    protected void load(WebContext ctx, Entity entity, Column... properties) {
        Set<String> columnsSet = Arrays.asList(properties).stream().map(Column::getName).collect(Collectors.toSet());
        for (Property property : entity.getDescriptor().getProperties()) {
            if (columnsSet.contains(property.getName())) {
                if (ctx.hasParameter(property.getName())) {
                    property.parseValue(entity, ctx.get(property.getName()));
                }
            }
        }
    }

    private boolean isAutoloaded(Property property) {
        Autoloaded autoloaded = property.getAnnotation(Autoloaded.class);
        if (autoloaded == null) {
            return false;
        }
        if (autoloaded.permissions().length > 0) {
            return UserContext.getCurrentUser().hasPermissions(autoloaded.permissions());
        } else {
            return true;
        }
    }

    protected <E extends Entity> E find(Class<E> type, String id) {
        if (BizEntity.NEW.equals(id) && BizEntity.class.isAssignableFrom(type)) {
            try {
                return type.newInstance();
            } catch (Throwable e) {
                throw Exceptions.handle()
                                .to(LOG)
                                .error(e)
                                .withSystemErrorMessage("Cannot create a new instance of '%s'", type.getName())
                                .handle();
            }
        }
        Optional<E> result = oma.find(type, id);
        if (!result.isPresent()) {
            throw Exceptions.createHandled().withNLSKey("BizController.unknownObject").set("id", id).handle();
        }
        return result.get();
    }

    protected <E extends Entity> Optional<E> tryFind(Class<E> type, String id) {
        if (BizEntity.NEW.equals(id)) {
            return Optional.empty();
        }
        return oma.find(type, id);
    }

    protected <E extends Entity> E findForTenant(Class<E> type, String id) {
        E result = find(type, id);
        if (!result.isNew() && result instanceof TenantAware) {
            assertTenant((TenantAware) result);
        }
        return result;
    }

    protected <E extends Entity> Optional<E> tryFindForTenant(Class<E> type, String id) {
        return tryFind(type, id).map(e -> {
            if (e instanceof TenantAware) {
                assertTenant((TenantAware) e);
            }
            return e;
        });
    }

    protected UserAccount currentUser() {
        if (!UserContext.getCurrentUser().isLoggedIn()) {
            return null;
        }
        return UserContext.getCurrentUser().getUserObject(UserAccount.class);
    }

    protected Tenant currentTenant() {
        if (!UserContext.getCurrentUser().isLoggedIn()) {
            return null;
        }
        return currentUser().getTenant().getValue();
    }
}
