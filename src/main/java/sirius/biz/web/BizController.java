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
import sirius.db.mixing.Column;
import sirius.db.mixing.Entity;
import sirius.db.mixing.EntityRef;
import sirius.db.mixing.OMA;
import sirius.db.mixing.Property;
import sirius.db.mixing.properties.BooleanProperty;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.web.controller.BasicController;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Base class for all controllers which operate on entities.
 * <p>
 * Provides glue logic for filling entites from {@link WebContext}s and for resolving entities for a given id.
 *
 * @see Entity
 */
public class BizController extends BasicController {

    @Part
    protected OMA oma;

    @Part
    protected Tenants tenants;

    /**
     * Contains the central logger for biz-relatet messages.
     */
    public static final Log LOG = Log.get("biz");

    /**
     * Ensures that the tenant of the current user matches the tenant of the given entity.
     *
     * @param tenantAware the entity to check
     * @throws sirius.kernel.health.HandledException if the tenants do no match
     */
    protected void assertTenant(TenantAware tenantAware) {
        if (currentTenant() == null && tenantAware.getTenant().getId() != null) {
            throw Exceptions.createHandled().withNLSKey("BizController.invalidTenant").handle();
        }

        if (currentTenant().getId() != tenantAware.getTenant().getId()) {
            throw Exceptions.createHandled().withNLSKey("BizController.invalidTenant").handle();
        }
    }

    /**
     * Enusures or establishes a parent child relation.
     * <p>
     * For new entities (owner), the given reference is initialized with the given entity. For existing entities
     * it is verified, that the given reference points to the given entity.
     *
     * @param owner  the entity which contains the reference
     * @param ref    the reference which is either filled or verified that it points to <tt>entity</tt>
     * @param entity the entity the reference must point to
     * @throws sirius.kernel.health.HandledException if the tenants do no match
     */
    protected <E extends Entity> void setOrVerify(Entity owner, EntityRef<E> ref, E entity) {
        if (!Objects.equals(ref.getId(), entity.getId())) {
            if (owner.isNew()) {
                ref.setValue(entity);
            } else {
                throw Exceptions.createHandled().withNLSKey("BizController.invalidReference").handle();
            }
        }
    }

    /**
     * Ensures that the given entity is already persisted in the database.
     *
     * @param obj the entity to check
     * @throws sirius.kernel.health.HandledException if the entity is still new and not yet persisted in the database
     */
    protected void assertNotNew(Entity obj) {
        assertNotNull(obj);
        if (obj.isNew()) {
            throw Exceptions.createHandled().withNLSKey("BizController.mustNotBeNew").handle();
        }
    }

    @ConfigValue("product.baseUrl")
    private String baseUrl;

    /**
     * Returns the base URL of this instance.
     *
     * @return the base URL like <tt>http://www.mydomain.stuff</tt>
     */
    protected String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Fetches all <tt>autoloaded</tt> fields of the given entity from the given request and populates the entity.
     *
     * @param ctx    the request to read parameters from
     * @param entity the entity to fill
     * @see Autoloaded
     */
    protected void load(WebContext ctx, Entity entity) {
        for (Property property : entity.getDescriptor().getProperties()) {
            if (shouldAutoload(ctx, property)) {
                property.parseValue(entity, ctx.get(property.getName()));
            }
        }
    }

    private boolean shouldAutoload(WebContext ctx, Property property) {
        if (!isAutoloaded(property)) {
            return false;
        }

        // If the parameter is present in the request we're good to go
        if (ctx.hasParameter(property.getName())) {
            return true;
        }

        // If the property is a boolean one, it will most probably handled
        // by a checkbox. As an unchecked checkbox will not submit any value
        // we still process this property, which is then considered to be
        // false (matching the unchecked checkbox).
        return property instanceof BooleanProperty;
    }

    /**
     * Reads the given properties from the given request and populates the given entity.
     *
     * @param ctx        the request to read parameters from
     * @param entity     the entity to fill
     * @param properties the list of properties to transfer
     */

    protected void load(WebContext ctx, Entity entity, Column... properties) {
        Set<String> columnsSet = Arrays.stream(properties).map(Column::getName).collect(Collectors.toSet());
        for (Property property : entity.getDescriptor().getProperties()) {
            if (columnsSet.contains(property.getName())) {
                property.parseValue(entity, ctx.get(property.getName()));
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

    /**
     * Tries to find an entity of the given type with the given id.
     * <p>
     * Note, if <tt>new</tt> is given as id, a new entity is created. This permits many editors to create a
     * new entity simply by calling /editor-uri/new
     *
     * @param type the type of the entity to find
     * @param id   the id to lookup
     * @param <E>  the generic type of the entity class
     * @return the requested entity or a new one, if id was <tt>new</tt>
     * @throws sirius.kernel.health.HandledException if either the id is unknown or a new instance cannot be created
     */
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

    /**
     * Tries to find an existing entity with the given id.
     *
     * @param type the type of the entity to find
     * @param id   the id of the entity to find
     * @param <E>  the generic type of the entity class
     * @return the requested entity wrapped as <tt>Optional</tt> or an empty optional, if no entity with the given id
     * was found
     * or if the id was <tt>new</tt>
     */
    protected <E extends Entity> Optional<E> tryFind(Class<E> type, String id) {
        if (BizEntity.NEW.equals(id)) {
            return Optional.empty();
        }
        return oma.find(type, id);
    }

    /**
     * Tries to find an entity for the given id, which belongs to the current tenant.
     * <p>
     * This behaves just like {@link #find(Class, String)} but once an existing entity was found, which also extends
     * {@link TenantAware}, it is ensured (using {@link #assertTenant(TenantAware)} that it belongs to the current
     * tenant.
     *
     * @param type the type of the entity to find
     * @param id   the id of the entity to find
     * @param <E>  the generic type of the entity class
     * @return the requested entity, which is either new or belongs to the current tenant
     */
    protected <E extends Entity> E findForTenant(Class<E> type, String id) {
        E result = find(type, id);
        if (!result.isNew() && result instanceof TenantAware) {
            assertTenant((TenantAware) result);
        }
        return result;
    }

    /**
     * Tries to find an entity for the given id, which belongs to the current tenant.
     * <p>
     * This behaves just like {@link #tryFind(Class, String)} but once an existing entity was found, which also extends
     * {@link TenantAware}, it is ensured (using {@link #assertTenant(TenantAware)} that it belongs to the current
     * tenant.
     *
     * @param type the type of the entity to find
     * @param id   the id of the entity to find
     * @param <E>  the generic type of the entity class
     * @return the requested entity, which belongs to the current tenant, wrapped as <tt>Optional</tt> or an empty
     * optional.
     */
    protected <E extends Entity> Optional<E> tryFindForTenant(Class<E> type, String id) {
        return tryFind(type, id).map(e -> {
            if (e instanceof TenantAware) {
                assertTenant((TenantAware) e);
            }
            return e;
        });
    }

    /**
     * Returns the {@link UserAccount} instance which belongs to the current user.
     *
     * @return the <tt>UserAccount</tt> instance of the current user or <tt>null</tt> if no user is logged in
     */
    protected UserAccount currentUser() {
        if (!UserContext.getCurrentUser().isLoggedIn()) {
            return null;
        }
        return UserContext.getCurrentUser().getUserObject(UserAccount.class);
    }

    /**
     * Returns the {@link Tenant} instance which belongs to the current user.
     *
     * @return the <tt>Tenant</tt> instance of the current user or <tt>null</tt> if no user is logged in
     */
    protected Tenant currentTenant() {
        if (!UserContext.getCurrentUser().isLoggedIn()) {
            return null;
        }
        return currentUser().getTenant().getValue();
    }
}
