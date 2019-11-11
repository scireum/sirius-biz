/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.process.PersistencePeriod;
import sirius.biz.process.Processes;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.tenants.Tenants;
import sirius.db.es.Elastic;
import sirius.db.jdbc.OMA;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.InvalidFieldException;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.Mixing;
import sirius.db.mixing.Property;
import sirius.db.mixing.properties.BaseEntityRefProperty;
import sirius.db.mixing.properties.BooleanProperty;
import sirius.db.mixing.types.BaseEntityRef;
import sirius.db.mongo.Mango;
import sirius.kernel.async.Tasks;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Values;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;
import sirius.web.controller.BasicController;
import sirius.web.controller.Message;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;
import sirius.web.util.LinkBuilder;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
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
    private Processes processes;

    @Part
    private Tasks tasks;

    /**
     * Provides access to the generic tenants manager.
     * <p>
     * Note that a concrete implementation like {@link sirius.biz.tenants.jdbc.SQLTenants} can be accessed by
     * using {@code tenants.as(SQLTenants.class)}. This also applies for {@link sirius.biz.tenants.mongo.MongoTenants}.
     */
    @Part
    protected Tenants<?, ?, ?> tenants;

    @ConfigValue("product.baseUrl")
    private static String baseUrl;
    private static boolean baseUrlChecked;

    @ConfigValue("controller.secret")
    private static String secret;

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

    protected HandledException invalidTenantException() {
        return Exceptions.createHandled().withNLSKey("BizController.invalidTenant").handle();
    }

    /**
     * Enusures or establishes a parent child relation.
     * <p>
     * For new entities (child), the given reference is initialized with the given entity. For existing entities
     * it is verified, that the given reference points to the given entity.
     *
     * @param child        the entity which contains the reference
     * @param ref          the reference which is either to be filled or verified that it points to <tt>parentEntity</tt>
     * @param parentEntity the entity the reference must point to
     * @param <E>          the generic type the the parent being referenced
     * @param <I>          the type of the id column of E
     * @throws sirius.kernel.health.HandledException if the entities do no match
     */
    protected <I, E extends BaseEntity<I>> void setOrVerifyParent(BaseEntity<?> child,
                                                                  BaseEntityRef<I, E> ref,
                                                                  E parentEntity) {
        if (!Objects.equals(ref.getId(), parentEntity.getId())) {
            if (child.isNew()) {
                ref.setValue(parentEntity);
            } else {
                throw Exceptions.createHandled()
                                .withNLSKey("BizController.invalidReference")
                                .set("child", child.getUniqueName())
                                .set("parent", parentEntity.getUniqueName())
                                .set("actual", ref.getUniqueObjectName())
                                .handle();
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
            throw entityNotNewException(obj.getClass());
        }
    }

    /**
     * Provides a simple {@link HandledException} to throw if a new entity was found when not allowed.
     * <p>
     * This exception is not logged.
     *
     * @param type the type of entity that was not found
     * @return a HandledException which can be thrown
     */
    protected <E extends BaseEntity<?>> HandledException entityNotNewException(Class<E> type) {
        return Exceptions.createHandled().withNLSKey("BizController.mustNotBeNew").set("type", type).handle();
    }

    /**
     * Returns the base URL of this instance.
     *
     * @return the base URL like <tt>http://www.mydomain.stuff</tt>
     */
    public static String getBaseUrl() {
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
                property.parseValues(entity, Values.of(ctx.getParameters(propertyName)));
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
     * Creates a {@link SaveHelper} with provides a fluent API to save an entity into the database.
     *
     * @param ctx the current request
     * @return a helper used to configure the save process
     */
    protected SaveHelper prepareSave(WebContext ctx) {
        return new SaveHelper(this, ctx);
    }

    /**
     * Deletes the entity with the given type and id.
     * <p>
     * If the entity is {@link TenantAware} a matching tenant will be ensured. If the entits
     * does no longer exist, this call will be ignored. If no valid POST with CSRF token is present,
     * and exception will be thrown.
     *
     * @param ctx  the current request
     * @param type the type of entity to delete
     * @param id   the id of the entity to delete
     */
    public void deleteEntity(WebContext ctx, Class<? extends BaseEntity<?>> type, String id) {
        deleteEntity(ctx, tryFindForTenant(type, id));
    }

    /**
     * Deletes the entity with the given type and id.
     * <p>
     * If the given optional is empty, this call will be ignored. If no valid POST with CSRF token is present,
     * and exception will be thrown.
     *
     * @param ctx            the current request
     * @param optionalEntity the entity to delete (if present)
     */
    public void deleteEntity(WebContext ctx, Optional<? extends BaseEntity<?>> optionalEntity) {
        if (ctx.isSafePOST()) {
            optionalEntity.ifPresent(entity -> {
                if (entity.getDescriptor().isComplexDelete() && processes != null) {
                    deleteComplexEntity(entity);
                } else {
                    entity.getDescriptor().getMapper().delete(entity);
                    showDeletedMessage();
                }
            });
        }
    }

    protected void deleteComplexEntity(BaseEntity<?> entity) {
        String processId = processes.createProcessForCurrentUser("delete-entity",
                                                                 NLS.fmtr("BizController.deleteProcessTitle")
                                                                    .set("entity", Strings.limit(entity, 30))
                                                                    .format(),
                                                                 "fa-trash",
                                                                 PersistencePeriod.THREE_MONTHS,
                                                                 Collections.emptyMap());
        tasks.defaultExecutor().fork(() -> {
            processes.execute(processId, process -> {
                process.log(ProcessLog.info()
                                      .withNLSKey("BizController.startDelete")
                                      .withContext("entity", String.valueOf(entity)));
                entity.getDescriptor().getMapper().delete(entity);
                process.log(ProcessLog.success().withNLSKey("BizController.deleteCompleted"));
            });
        });

        UserContext.message(Message.info(NLS.get("BizController.deletingInBackground"))
                                   .withAction("/ps/" + processId, NLS.get("BizController.deleteProcess")));
    }

    /**
     * Performs a validation and reports the first warnings via the {@link UserContext}.
     * <p>
     * Note that this is only done, if no error messages are present which might otherwise confuse the user.
     *
     * @param entity the entity to validate
     */
    protected void validate(BaseEntity<?> entity) {
        UserContext userCtx = UserContext.get();
        if (userCtx.getMessages().stream().noneMatch(msg -> Strings.areEqual(Message.ERROR, msg.getType()))) {
            entity.getMapper().validate(entity).stream().findFirst().ifPresent(msg -> {
                userCtx.addMessage(Message.warn(msg));
            });
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
        Optional<E> result = mixing.getDescriptor(type).getMapper().find(type, id);
        if (!result.isPresent()) {
            throw entityNotFoundException(type, id);
        }
        return result.get();
    }

    /**
     * Provides a simple {@link HandledException} to throw if entity is not found.
     * <p>
     * If the entity is new, {@link #entityNotNewException(Class)} is provided.
     *
     * @param type the type of entity that was not found
     * @param id   the id of the entity that was not found
     * @return a HandledException which can be thrown
     */
    protected <E extends BaseEntity<?>> HandledException entityNotFoundException(@Nonnull Class<E> type,
                                                                                 @Nonnull String id) {
        if (BaseEntity.NEW.equals(id)) {
            return entityNotNewException(type);
        }
        return Exceptions.createHandled()
                         .withNLSKey("BizController.unknownObject")
                         .set("type", type.getSimpleName())
                         .set("id", id)
                         .handle();
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
    protected <E extends BaseEntity<?>> Optional<E> tryFind(Class<E> type, String id) {
        if (BaseEntity.NEW.equals(id)) {
            return Optional.empty();
        }
        return mixing.getDescriptor(type).getMapper().find(type, id);
    }

    /**
     * Tries to find an existing entity with the given id, or fails otherwise.
     *
     * @param type the type of the entity to find
     * @param id   the id of the entity to find
     * @param <E>  the generic type of the entity class
     * @return the requested entity wrapped
     * @throws HandledException if no entity with the given id was found or if the id was <tt>new</tt>
     */
    protected <E extends BaseEntity<?>> E findExisting(Class<E> type, String id) {
        return tryFind(type, id).orElseThrow(() -> entityNotFoundException(type, id));
    }

    /**
     * Tries to find an entity for the given id, which belongs to the current tenant.
     * <p>
     * This behaves just like {@link #find(Class, String)} but once an existing entity was found, which also extends
     * {@link TenantAware}, it is ensured (using {@link #assertTenant(TenantAware)} that it belongs to the current
     * tenant. If the entity is new, the tenant is filled with the current tenant.
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

    /**
     * Tries to find an entity for the given id, which belongs to the current tenant or fails otherwise.
     * <p>
     * This behaves just like {@link #findExisting(Class, String)} but once an existing entity was found, which also extends
     * {@link TenantAware}, it is ensured (using {@link #assertTenant(TenantAware)} that it belongs to the current
     * tenant.
     *
     * @param type the type of the entity to find
     * @param id   the id of the entity to find
     * @param <E>  the generic type of the entity class
     * @return the requested entity, which belongs to the current tenant
     * @throws HandledException if no entity with the given id was found or if the id was <tt>new</tt>
     */
    protected <E extends BaseEntity<?>> E findExistingForTenant(Class<E> type, String id) {
        E result = findExisting(type, id);
        if (result instanceof TenantAware) {
            assertTenant((TenantAware) result);
        }
        return result;
    }

    /**
     * Handles the given exception.
     * <p>
     * In contrast to {@link UserContext#handle(Throwable)} this will also check if the
     * cause of the given exception is a {@link InvalidFieldException} generated by sirius-db.
     * <p>
     * In this case, it will mark the according field as errorneous.
     *
     * @param ex the exception to handle
     */
    protected void handle(Exception ex) {
        if (ex.getCause() instanceof InvalidFieldException) {
            UserContext.get().signalFieldError(((InvalidFieldException) ex.getCause()).getField());
        }

        UserContext.handle(ex);
    }

    /**
     * Computes a signature used by {@link #signLink(String)} and {@link #verifySignedLink(WebContext)}.
     *
     * @param uri the uri to sign
     * @return a signature (hash) based an the given URL, a timestamp and <tt>controller.secret</tt> from the
     * system configuration
     */
    public static String computeURISignature(String uri) {
        if (Strings.isEmpty(secret)) {
            secret = Strings.generateCode(32);
        }

        long unixTimeInDays = TimeUnit.DAYS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        return Hashing.md5().hashString(uri + secret + unixTimeInDays, Charsets.UTF_8).toString();
    }

    /**
     * Signs the given link by appending an authentication hash.
     * <p>
     * This can be used to pre-sign url which then can be processed by other controllers without
     * additional security / permission checks. This can be useful for generic frameworks which
     * have to delegate permission checks to their callers / previous controllers.
     *
     * @param link the link to enhance
     * @return the link with an appropriate <tt>controllerAuthHash</tt> as generated by
     * {@link #computeURISignature(String)} and therefore accepted by {@link #verifySignedLink(WebContext)}.
     */
    public static String signLink(String link) {
        LinkBuilder linkBuilder = new LinkBuilder(link);
        Tuple<String, String> uriAndQueryString = Strings.split(link, "?");
        String hash = computeURISignature(uriAndQueryString.getFirst());
        return linkBuilder.append("controllerAuthHash", hash).toString();
    }

    /**
     * Verifies that the given request contains a valid auth hash for its URI.
     * <p>
     * Note that if this method returns <tt>false</tt>, the request is already handled and the controller should
     * abort further processing.
     *
     * @param ctx the current request
     * @return <tt>true</tt> if the link if properly signed, <tt>false</tt> otherwise. In this case a response has
     * already been sent.
     */
    public static boolean verifySignedLink(WebContext ctx) {
        String hash = computeURISignature(ctx.getRequestedURI());
        if (!Strings.areEqual(hash, ctx.get("controllerAuthHash").asString())) {
            ctx.respondWith().error(HttpResponseStatus.FORBIDDEN, "Security hash does not match!");
            return false;
        }

        return true;
    }
}

