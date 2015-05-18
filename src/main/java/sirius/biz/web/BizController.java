/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.biz.model.BizEntity;
import sirius.biz.tenants.TenantAware;
import sirius.biz.tenants.Tenants;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;
import sirius.mixing.Column;
import sirius.mixing.Entity;
import sirius.mixing.OMA;
import sirius.mixing.Property;
import sirius.web.controller.Controller;
import sirius.web.controller.Message;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Created by aha on 08.05.15.
 */
public class BizController implements Controller {

    @Part
    protected OMA oma;

    @Part
    protected Tenants tenants;

    public static final Log LOG = Log.get("biz");


    protected UserInfo getUser() {
        return UserContext.getCurrentUser();
    }

    protected boolean hasPermission(String permission) {
        return getUser().hasPermission(permission);
    }

    protected void assertPermission(String permission) {

    }

    protected void assertTenant(TenantAware tenantAware) {

    }

    protected void assertNotNull(Object obj) {

    }

    protected void assertNotNew(Object obj) {

    }

    protected Consumer<WebContext> defaultRoute;

    public BizController() {
        Optional<Method> defaultMethod = Arrays.stream(getClass().getDeclaredMethods())
                                               .filter(m -> m.isAnnotationPresent(DefaultRoute.class))
                                               .findFirst();
        if (!defaultMethod.isPresent()) {
            throw new IllegalStateException(Strings.apply("Controller %s has no default route!", getClass().getName()));
        }
        this.defaultRoute = ctx -> {
            try {
                defaultMethod.get().invoke(this, ctx);
            } catch (IllegalAccessException e) {
                throw Exceptions.handle(e);
            } catch (InvocationTargetException e) {
                throw Exceptions.handle(e.getTargetException());
            }
        };
    }

    @Override
    public void onError(WebContext ctx, HandledException error) {
        if (error != null) {
            UserContext.message(Message.error(error.getMessage()));
        }
        defaultRoute.accept(ctx);
    }

    @ConfigValue("product.baseUrl")
    private String baseUrl;

    protected String getBaseUrl() {
        return baseUrl;
    }


    public void showSavedMessage() {
        UserContext.message(Message.info(NLS.get("BizController.changesSaved")));
    }

    public void showDeletedMessage() {
        UserContext.message(Message.info(NLS.get("BizController.objectDeleted")));
    }

    protected void load(WebContext ctx, Entity entity, Column... columns) {
        for (Column c : columns) {
            Property property = entity.getDescriptor().getProperty(c);
            if (property == null) {
                throw new IllegalArgumentException(Strings.apply("Unknown property '%s' for type '%s'",
                                                                 c,
                                                                 entity.getClass().getName()));
            }
            property.parseValue(entity, ctx.get(property.getName()));
        }
    }

    protected <E extends BizEntity> E find(Class<E> type, String id) {
        if (BizEntity.NEW.equals(id)) {
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

    protected <E extends BizEntity> Optional<E> tryFind(Class<E> type, String id) {
        if (BizEntity.NEW.equals(id)) {
            return Optional.empty();
        }
        return oma.find(type, id);
    }

    protected <E extends BizEntity> E findForTenant(Class<E> type, String id) {
        E result = find(type, id);
        if (!result.isNew() && result instanceof TenantAware) {
            assertTenant((TenantAware) result);
        }
        return result;
    }

    protected <E extends BizEntity> Optional<E> tryFindForTenant(Class<E> type, String id) {
        return tryFind(type, id).map(e -> {
            if (e instanceof TenantAware) {
                assertTenant((TenantAware) e);
            }
            return e;
        });
    }
}
