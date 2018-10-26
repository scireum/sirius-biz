/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.biz.tenants.Tenants;
import sirius.db.es.Elastic;
import sirius.db.jdbc.OMA;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.BaseMapper;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.Mixing;
import sirius.db.mixing.Property;
import sirius.db.mixing.properties.BaseEntityRefProperty;
import sirius.db.mixing.properties.BooleanProperty;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.db.mongo.Mango;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.kernel.nls.Formatter;
import sirius.web.controller.BasicController;
import sirius.web.controller.Message;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Base class for all controllers which operate on entities.
 * <p>
 * Provides glue logic for filling entites from {@link WebContext}s and for resolving entities for a given id.
 */
public class BizController extends BasicController {

    @Part
    protected Mixing mixing;

    @Part
    protected OMA oma;

    @Part
    protected Mango mango;

    @Part
    protected Elastic elastic;

    @Part
    protected Tenants tenants;

    @ConfigValue("product.baseUrl")
    private String baseUrl;
    private static boolean baseUrlChecked;

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
        if (tenantAware == null) {
            return;
        }

        if (!UserContext.getCurrentUser().isLoggedIn() && tenantAware.getTenantAsString() != null) {
            throw invalidTenantException();
        }

        assertTenant(tenantAware.getTenantAsString());
    }

    /**
     * Ensures that the tenant of the current user matches the given tenant id.
     *
     * @param tenantId the id to check
     * @throws sirius.kernel.health.HandledException if the tenants do no match
     */
    protected void assertTenant(@Nonnull String tenantId) {
        if (!Objects.equals(UserContext.getCurrentUser().getTenantId(), tenantId)) {
            throw invalidTenantException();
        }
    }

    private HandledException invalidTenantException() {
        return Exceptions.createHandled().withNLSKey("BizController.invalidTenant").handle();
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
     * @param <E>    the generic type the the entity being referenced
     * @throws sirius.kernel.health.HandledException if the entities do no match
     */
    protected <I, E extends BaseEntity<I>> void setOrVerify(BaseEntity<?> owner, BaseEntityRef<I, E> ref, E entity) {
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
    protected void assertNotNew(BaseEntity<?> obj) {
        assertNotNull(obj);
        if (obj.isNew()) {
            throw Exceptions.createHandled().withNLSKey("BizController.mustNotBeNew").handle();
        }
    }

    /**
     * Returns the base URL of this instance.
     *
     * @return the base URL like <tt>http://www.mydomain.stuff</tt>
     */
    protected String getBaseUrl() {
        if (!baseUrlChecked) {
            baseUrlChecked = true;
            if (Strings.isEmpty(baseUrl)) {
                LOG.WARN("product.baseUrl is not filled. Please update the system configuration!");
            }
        }

        if (UserContext.getSettings().getConfig().hasPath("tenant.baseUrl")) {
            return UserContext.getSettings().get("tenant.baseUrl").asString();
        }

        return baseUrl;
    }

    /**
     * Fetches all <tt>autoloaded</tt> fields of the given entity from the given request and populates the entity.
     *
     * @param ctx    the request to read parameters from
     * @param entity the entity to fill
     * @see Autoloaded
     */
    protected void load(WebContext ctx, BaseEntity<?> entity) {
        List<Mapping> columns = entity.getDescriptor()
                                      .getProperties()
                                      .stream()
                                      .filter(property -> shouldAutoload(ctx, property))
                                      .map(Property::getName)
                                      .map(Mapping::named)
                                      .collect(Collectors.toList());

        load(ctx, entity, columns);
    }

    /**
     * Reads the given properties from the given request and populates the given entity.
     *
     * @param ctx        the request to read parameters from
     * @param entity     the entity to fill
     * @param properties the list of properties to transfer
     */
    protected void load(WebContext ctx, BaseEntity<?> entity, Mapping... properties) {
        load(ctx, entity, Arrays.asList(properties));
    }

    protected void load(WebContext ctx, BaseEntity<?> entity, List<Mapping> properties) {
        boolean hasError = false;

        for (Mapping columnProperty : properties) {
            Property property = entity.getDescriptor().getProperty(columnProperty);
            String propertyName = property.getName();

            try {
                property.parseValue(entity, ctx.get(propertyName));
                ensureTenantMatch(entity, property);
            } catch (HandledException e) {
                UserContext.setFieldError(propertyName, ctx.get(propertyName));
                UserContext.setErrorMessage(propertyName, e.getMessage());

                hasError = true;
            }
        }

        if (hasError) {
            throw Exceptions.createHandled().withNLSKey("BizController.illegalArgument").handle();
        }
    }

    private void ensureTenantMatch(BaseEntity<?> entity, Property property) {
        if ((entity instanceof TenantAware) && property instanceof BaseEntityRefProperty) {
            Object loadedEntity = property.getValue(entity);
            if (loadedEntity instanceof TenantAware) {
                ((TenantAware) entity).assertSameTenant(property::getLabel, (TenantAware) loadedEntity);
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

    private boolean isAutoloaded(Property property) {
        Autoloaded autoloaded = property.getAnnotation(Autoloaded.class).orElse(null);
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
     * Provides a fluent API to control the process and user routing while creating or updating an entity in the
     * database.
     */
    public class SaveHelper {

        private WebContext ctx;
        private Consumer<Boolean> preSaveHandler;
        private Consumer<Boolean> postSaveHandler;
        private String createdURI;
        private String afterSaveURI;

        private List<Mapping> mappings;
        private boolean autoload = true;
        private boolean acceptUnsafePOST = false;

        private SaveHelper(WebContext ctx) {
            this.ctx = ctx;
        }

        /**
         * Installs a pre save handler which is invoked just before the entity is persisted into the database.
         *
         * @param preSaveHandler a consumer which is supplied with a boolean flag, indicating if the entity was new.
         *                       The handler can be used to modify the entity before it is saved.
         * @return the helper itself for fluent method calls
         */
        public SaveHelper withPreSaveHandler(Consumer<Boolean> preSaveHandler) {
            this.preSaveHandler = preSaveHandler;
            return this;
        }

        /**
         * Installs a post save handler which is invoked just after the entity was persisted into the database.
         *
         * @param postSaveHandler a consumer which is supplied with a boolean flag, indicating if the entiy was new.
         *                        The
         *                        handler can be used to modify the entity or related entities after it was created in
         *                        the database.
         * @return the helper itself for fluent method calls
         */
        public SaveHelper withPostSaveHandler(Consumer<Boolean> postSaveHandler) {
            this.postSaveHandler = postSaveHandler;
            return this;
        }

        /**
         * Specifies what mappings should be loaded from the request context
         * <p>
         * if not set all marked as {@link Autoloaded} properties of the entity are loaded
         *
         * @param columns array of {@link Mapping} objects
         * @return the helper itself for fluent method calls
         */
        public SaveHelper withMappings(Mapping... columns) {
            this.mappings = Arrays.asList(columns);
            return this;
        }

        /**
         * Used to supply a URL to which the user is redirected if a new entity was created.
         * <p>
         * As new entities are often created using a placeholder URL like <tt>/entity/new</tt>, we must
         * redirect to the canonical URL like <tt>/entity/128</tt> if a new entity was created.
         * <p>
         * Note that the redirect is only performed if the newly created entity has validation warnings or the Entity is
         * new.
         *
         * @param createdURI the URI to redirect to where <tt>${id}</tt> is replaced with the actual id of the entity
         * @return the helper itself for fluent method calls
         */
        public SaveHelper withAfterCreateURI(String createdURI) {
            this.createdURI = createdURI;
            return this;
        }

        /**
         * Used to supply a URL to which the user is redirected if an entity was successfully saved.
         * <p>
         * Once an entity was successfully saved is not new and has no validation warnings, the user will be redirected
         * to the given URL.
         *
         * @param afterSaveURI the list or base URL to return to, after an entity was successfully edited.
         * @return the helper itself for fluent method calls
         */
        public SaveHelper withAfterSaveURI(String afterSaveURI) {
            this.afterSaveURI = afterSaveURI;
            return this;
        }

        /**
         * Disables the automatically loading process of all entity properties annotated with {@link Autoloaded}.
         *
         * @return the helper itself for fluent method calls
         */
        public SaveHelper disableAutoload() {
            this.autoload = false;
            return this;
        }

        /**
         * Disables the CSRF-token checks when {@link #saveEntity(BaseEntity)} is called.
         *
         * @return the helper itself for fluent method calls
         */
        public SaveHelper disableSafePOST() {
            this.acceptUnsafePOST = true;
            return this;
        }

        /**
         * Applies the configured save login on the given entity.
         *
         * @param entity the entity to update and save
         * @return <tt>true</tt> if the request was handled (the user was redirected), <tt>false</tt> otherwise
         */
        public boolean saveEntity(BaseEntity<?> entity) {
            try {
                if (!((acceptUnsafePOST && ctx.isUnsafePOST()) || ctx.ensureSafePOST())) {
                    return false;
                }

                boolean wasNew = entity.isNew();

                if (autoload) {
                    load(ctx, entity);
                }

                if (mappings != null && !mappings.isEmpty()) {
                    load(ctx, entity, mappings);
                }

                if (preSaveHandler != null) {
                    preSaveHandler.accept(wasNew);
                }

                entity.getMapper().update(entity);
                if (postSaveHandler != null) {
                    postSaveHandler.accept(wasNew);
                }

                if (wasNew && Strings.isFilled(createdURI)) {
                    ctx.respondWith()
                       .redirectToGet(Formatter.create(createdURI).set("id", entity.getIdAsString()).format());
                    return true;
                }

                if (!entity.getMapper().hasValidationWarnings(entity) && Strings.isFilled(afterSaveURI)) {
                    ctx.respondWith()
                       .redirectToGet(Formatter.create(afterSaveURI).set("id", entity.getIdAsString()).format());
                    return true;
                }
                showSavedMessage();
            } catch (Exception e) {
                UserContext.handle(e);
            }
            return false;
        }
    }

    /**
     * Creates a {@link SaveHelper} with provides a fluent API to save an entity into the database.
     *
     * @param ctx the current request
     * @return a helper used to configure the save process
     */
    protected SaveHelper prepareSave(WebContext ctx) {
        return new SaveHelper(ctx);
    }

    /**
     * Performs a validation and reports all warnings via the {@link UserContext}.
     *
     * @param entity the entity to validate
     */
    protected void validate(BaseEntity<?> entity) {
        for (String warning : entity.getMapper().validate(entity)) {
            UserContext.message(Message.warn(warning));
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
    @SuppressWarnings("unchecked")
    protected <E extends BaseEntity<?>> E find(Class<E> type, String id) {
        if (BaseEntity.NEW.equals(id) && BaseEntity.class.isAssignableFrom(type)) {
            try {
                return type.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw Exceptions.handle()
                                .to(LOG)
                                .error(e)
                                .withSystemErrorMessage("Cannot create a new instance of '%s'", type.getName())
                                .handle();
            }
        }
        Optional<E> result = ((BaseMapper<BaseEntity<?>, ?, ?>) mixing.getDescriptor(type).getMapper()).find(type, id);
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
    @SuppressWarnings("unchecked")
    protected <E extends BaseEntity<?>> Optional<E> tryFind(Class<E> type, String id) {
        if (BaseEntity.NEW.equals(id)) {
            return Optional.empty();
        }
        return ((BaseMapper<BaseEntity<?>, ?, ?>) mixing.getDescriptor(type).getMapper()).find(type, id);
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
    protected <E extends BaseEntity<?>> E findForTenant(Class<E> type, String id) {
        E result = find(type, id);
        if (result instanceof TenantAware) {
            if (result.isNew()) {
                ((TenantAware) result).fillWithCurrentTenant();
            } else {
                assertTenant((TenantAware) result);
            }
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
    protected <E extends BaseEntity<?>> Optional<E> tryFindForTenant(Class<E> type, String id) {
        return tryFind(type, id).map(e -> {
            if (e instanceof TenantAware) {
                assertTenant((TenantAware) e);
            }
            return e;
        });
    }
}
