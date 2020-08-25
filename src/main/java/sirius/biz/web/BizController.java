/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

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
import sirius.db.mixing.types.BaseEntityRef;
import sirius.db.mongo.Mango;
import sirius.kernel.async.Tasks;
import sirius.kernel.commons.Hasher;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.commons.Values;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;
import sirius.web.controller.BasicController;
import sirius.web.controller.Message;
import sirius.web.controller.MessageLevel;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;
import sirius.web.util.LinkBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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

    /**
     * This suffix is used to find hidden input fields which show the presence of an unchecked checkbox which itself
     * doesn't submit anything back to the server.
     */
    private static final String CHECKBOX_PRESENCE_MARKER = "_marker";

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
    @Nullable
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
     * <p>
     * Note that this check is also provided by {@link Tenants#assertTenant(TenantAware)}. However,
     * we use an implementation here which works independently of the tenants framework as some
     * products do not use it.
     *
     * @param tenantAware the entity to check
     * @throws sirius.kernel.health.HandledException if the tenants do no match
     * @see Tenants#assertTenant(TenantAware)
     */
    protected void assertTenant(TenantAware tenantAware) {
        if (tenantAware != null) {
            assertTenant(tenantAware.getTenantAsString());
        }
    }

    /**
     * Ensures that the tenant of the current user matches the given tenant id.
     * <p>
     * Note that this check is also provided by {@link Tenants#assertTenant(String)}. However,
     * we use an implementation here which works independently of the tenants framework as some
     * products do not use it.
     *
     * @param tenantId the id to check
     * @throws sirius.kernel.health.HandledException if the tenants do no match
     * @see Tenants#assertTenant(String)
     */
    protected void assertTenant(@Nullable String tenantId) {
        if (Strings.isFilled(tenantId) && !Strings.areEqual(tenantId, UserContext.getCurrentUser().getTenantId())) {
            throw Exceptions.createHandled().withNLSKey("Tenants.invalidTenant").handle();
        }
    }

    /**
     * Properly creates or maintains a reference to an entity with {@link BaseEntityRef#hasWriteOnceSemantics()} write-once semantic}.
     * <p>
     * For new entities (owner), the given reference is initialized with the given target. For existing entities
     * it is verified, that the given reference points to the given target.
     * <p>
     * This method can also maintain references without a {@link BaseEntityRef#hasWriteOnceSemantics write-once semantic},
     * but this might indicate an inconsistent or invalid usage pattern and one should strongly consider using a reference
     * with {@link BaseEntityRef#hasWriteOnceSemantics write-once semantics}.
     *
     * @param owner  the entity which contains the reference
     * @param ref    the reference which is either to be filled or verified that it points to <tt>target</tt>
     * @param target the target the reference must point to
     * @param <E>    the generic type the the parent being referenced
     * @param <I>    the type of the id column of E
     * @throws sirius.kernel.health.HandledException if the entities do no match
     * @see BaseEntityRef#hasWriteOnceSemantics
     */
    protected <I, E extends BaseEntity<I>> void setOrVerify(BaseEntity<?> owner, BaseEntityRef<I, E> ref, E target) {
        if (!Objects.equals(ref.getId(), target.getId())) {
            if (owner.isNew()) {
                ref.setValue(target);
            } else {
                throw Exceptions.createHandled()
                                .withNLSKey("BizController.invalidReference")
                                .set("owner", owner.getUniqueName())
                                .set("target", target.getUniqueName())
                                .set("actual", ref.getUniqueObjectName())
                                .handle();
            }
        }
    }

    /**
     * Ensures that the given entity is already persisted in the database.
     *
     * @param entity the entity to check
     * @throws sirius.kernel.health.HandledException if the entity is still new and not yet persisted in the database
     */
    protected void assertNotNew(BaseEntity<?> entity) {
        assertNotNull(entity);
        if (entity.isNew()) {
            throw entityNotNewException(entity.getClass());
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
     * @param webContext the request to read parameters from
     * @param entity     the entity to fill
     * @see Autoloaded
     */
    protected void load(WebContext webContext, BaseEntity<?> entity) {
        List<Mapping> columns = entity.getDescriptor()
                                      .getProperties()
                                      .stream()
                                      .filter(property -> shouldAutoload(webContext, property))
                                      .map(Property::getName)
                                      .map(Mapping::named)
                                      .collect(Collectors.toList());

        load(webContext, entity, columns);
    }

    /**
     * Reads the given properties from the given request and populates the given entity.
     *
     * @param webContext the request to read parameters from
     * @param entity     the entity to fill
     * @param properties the list of properties to transfer
     */
    protected void load(WebContext webContext, BaseEntity<?> entity, Mapping... properties) {
        load(webContext, entity, Arrays.asList(properties));
    }

    protected void load(WebContext webContext, BaseEntity<?> entity, List<Mapping> properties) {
        boolean hasError = false;

        for (Mapping columnProperty : properties) {
            Property property = entity.getDescriptor().getProperty(columnProperty);

            if (!tryLoadProperty(webContext, entity, property)) {
                hasError = true;
            }
        }

        if (hasError) {
            throw Exceptions.createHandled().withNLSKey("BizController.illegalArgument").handle();
        }
    }

    private boolean tryLoadProperty(WebContext webContext, BaseEntity<?> entity, Property property) {
        String propertyName = property.getName();

        if (!webContext.hasParameter(propertyName) && !webContext.hasParameter(propertyName
                                                                               + CHECKBOX_PRESENCE_MARKER)) {
            // If the parameter is not present in the request we just skip it to prevent resetting the field to null
            return true;
        }
        Value parameterValue = webContext.get(propertyName);
        try {
            property.parseValues(entity,
                                 Values.of(parameterValue.get(List.class,
                                                              Collections.singletonList(parameterValue.get()))));
            ensureTenantMatch(entity, property);
        } catch (HandledException exception) {
            UserContext.setFieldError(propertyName, parameterValue);
            UserContext.setErrorMessage(propertyName, exception.getMessage());
            return false;
        }
        return true;
    }

    private void ensureTenantMatch(BaseEntity<?> entity, Property property) {
        if ((entity instanceof TenantAware) && property instanceof BaseEntityRefProperty) {
            Object loadedEntity = property.getValue(entity);
            if (loadedEntity instanceof TenantAware) {
                ((TenantAware) entity).assertSameTenant(property::getLabel, (TenantAware) loadedEntity);
            }
        }
    }

    private boolean shouldAutoload(WebContext webContext, Property property) {
        if (!isAutoloaded(property)) {
            return false;
        }

        // If the parameter is present in the request we're good to go
        if (webContext.hasParameter(property.getName())) {
            return true;
        }

        // We look for the presence of a marker which is added when the property is handled by one or multiple checkboxes,
        // as else we wouldn't know when to empty these fields, as empty checkboxes are not posted into the request.
        return webContext.hasParameter(property.getName() + CHECKBOX_PRESENCE_MARKER);
    }

    private boolean isAutoloaded(Property property) {
        Autoloaded autoloaded = property.getAnnotation(Autoloaded.class).orElse(null);
        return autoloaded != null && UserContext.getCurrentUser().hasPermissions(autoloaded.permissions());
    }

    /**
     * Creates a {@link SaveHelper} with provides a fluent API to save an entity into the database.
     *
     * @param webContext the current request
     * @return a helper used to configure the save process
     */
    protected SaveHelper prepareSave(WebContext webContext) {
        return new SaveHelper(this, webContext);
    }

    /**
     * Deletes the entity with the given type and id.
     * <p>
     * If the entity is {@link TenantAware} a matching tenant will be ensured. If the entits
     * does no longer exist, this call will be ignored. If no valid POST with CSRF token is present,
     * and exception will be thrown.
     *
     * @param webContext the current request
     * @param type       the type of entity to delete
     * @param id         the id of the entity to delete
     */
    public void deleteEntity(WebContext webContext, Class<? extends BaseEntity<?>> type, String id) {
        deleteEntity(webContext, tryFindForTenant(type, id));
    }

    /**
     * Deletes the entity with the given type and id.
     * <p>
     * If the given optional is empty, this call will be ignored. If no valid POST with CSRF token is present,
     * and exception will be thrown.
     *
     * @param webContext     the current request
     * @param optionalEntity the entity to delete (if present)
     */
    public void deleteEntity(WebContext webContext, Optional<? extends BaseEntity<?>> optionalEntity) {
        if (webContext.isSafePOST()) {
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
        tasks.defaultExecutor().fork(() -> processes.execute(processId, process -> {
            process.log(ProcessLog.info()
                                  .withNLSKey("BizController.startDelete")
                                  .withContext("entity", String.valueOf(entity)));
            entity.getDescriptor().getMapper().delete(entity);
            process.log(ProcessLog.success().withNLSKey("BizController.deleteCompleted"));
        }));

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
        if (userCtx.getMessages().stream().noneMatch(message -> MessageLevel.PROBLEM == message.getType())) {
            entity.getMapper()
                  .validate(entity)
                  .stream()
                  .findFirst()
                  .ifPresent(message -> userCtx.addMessage(Message.warn(message)));
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
            } catch (Exception exception) {
                throw Exceptions.handle()
                                .to(LOG)
                                .error(exception)
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
            ((TenantAware) result).setOrVerifyCurrentTenant();
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
     * @param exception the exception to handle
     */
    protected void handle(Exception exception) {
        if (exception.getCause() instanceof InvalidFieldException) {
            UserContext.get().signalFieldError(((InvalidFieldException) exception.getCause()).getField());
        }

        UserContext.handle(exception);
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
        return Hasher.md5().hash(uri + secret + unixTimeInDays).toHexString();
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
     * @param webContext the current request
     * @return <tt>true</tt> if the link if properly signed, <tt>false</tt> otherwise. In this case a response has
     * already been sent.
     */
    public static boolean verifySignedLink(WebContext webContext) {
        String hash = computeURISignature(webContext.getRequestedURI());
        if (!Strings.areEqual(hash, webContext.get("controllerAuthHash").asString())) {
            webContext.respondWith().error(HttpResponseStatus.FORBIDDEN, "Security hash does not match!");
            return false;
        }

        return true;
    }
}
