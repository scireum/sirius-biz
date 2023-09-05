/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Mapping;
import sirius.kernel.commons.Strings;
import sirius.kernel.nls.Formatter;
import sirius.web.http.WebContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Provides a fluent API to control the process and user routing while creating or updating an entity in the
 * database.
 */
public class SaveHelper {

    private final BizController bizController;
    private final WebContext ctx;
    private Consumer<Boolean> preSaveHandler;
    private Consumer<Boolean> postSaveHandler;
    private UnaryOperator<Boolean> skipCreatePredicate;
    private String createdURI;
    private String afterSaveURI;

    private List<Mapping> mappings;
    private boolean autoload = true;
    private boolean acceptUnsafePOST = false;
    private boolean saveMessage = true;

    SaveHelper(BizController bizController, WebContext ctx) {
        this.bizController = bizController;
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
     * @param postSaveHandler a consumer which is supplied with a boolean flag, indicating if the entity was new.
     *                        The handler can be used to modify the entity or related entities after it was created in
     *                        the database.
     * @return the helper itself for fluent method calls
     */
    public SaveHelper withPostSaveHandler(Consumer<Boolean> postSaveHandler) {
        this.postSaveHandler = postSaveHandler;
        return this;
    }

    /**
     * Installs a predicate which can inspect the underlying entity and determine if a save for a POST should be
     * attempted or not.
     * <p>
     * Note that these predicates are a bit exotic and only used if an entity is to be created in a two-step
     * process. These are entities where some fields need to be set (like a type) before other fields
     * can even be rendered (e.g. if their type or appearance depends on the type itself).
     *
     * @param skipCreatePredicate the predicate which decides if the entity should be persisted in the database
     *                            or if the editor should be rendered again. They receive the {@link BaseEntity#isNew()}
     *                            as if was before loading any data and may return <tt>true</tt> to save the entity
     *                            or <tt>false</tt> to show the editor again.
     * @return the helper itself for fluent method calls
     */
    public SaveHelper withSkipCreatePredicate(UnaryOperator<Boolean> skipCreatePredicate) {
        this.skipCreatePredicate = skipCreatePredicate;
        return this;
    }

    /**
     * Specifies what mappings should be loaded from the request context.
     * <p>
     * Note that by default also all properties wearing an {@link Autoloaded} annotation will be loaded. To suppress
     * this behaviour {@link #disableAutoload()} has to be called.
     *
     * @param columns array of {@link Mapping} objects
     * @return the helper itself for fluent method calls
     */
    public SaveHelper withMappings(Mapping... columns) {
        this.mappings = Arrays.asList(columns);
        return this;
    }

    /**
     * Specifies what mappings should be loaded from the request context.
     * <p>
     * Note that by default also all properties wearing an {@link Autoloaded} annotation will be loaded. To suppress
     * this behaviour {@link #disableAutoload()} has to be called.
     *
     * @param columns array of {@link Mapping} objects
     * @return the helper itself for fluent method calls
     */
    public SaveHelper withMappings(List<Mapping> columns) {
        this.mappings = new ArrayList<>(columns);
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
     * Disables the display of the {@linkplain BizController#showSavedMessage() "entity saved"} message.
     *
     * @return the helper itself for fluent method calls
     */
    public SaveHelper disableSaveMessage() {
        this.saveMessage = false;
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
                bizController.load(ctx, entity);
            }

            if (mappings != null && !mappings.isEmpty()) {
                bizController.load(ctx, entity, mappings);
            }

            if (preSaveHandler != null) {
                preSaveHandler.accept(wasNew);
            }

            if (skipCreatePredicate != null && Boolean.TRUE.equals(skipCreatePredicate.apply(wasNew))) {
                return false;
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

            if (saveMessage) {
                bizController.showSavedMessage();
            }

            if (!entity.getMapper().hasValidationWarnings(entity) && Strings.isFilled(afterSaveURI)) {
                ctx.respondWith()
                   .redirectToGet(Formatter.create(afterSaveURI).set("id", entity.getIdAsString()).format());
                return true;
            }
        } catch (Exception exception) {
            bizController.handle(exception);
        }
        return false;
    }
}
