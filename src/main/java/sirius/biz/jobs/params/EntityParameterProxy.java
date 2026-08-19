/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.biz.tenants.Tenants;
import sirius.biz.web.TenantAware;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.BaseMapper;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;
import sirius.kernel.commons.Value;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Provides the common mechanics shared by all entity based job parameters.
 * <p>
 * {@link EntityParameter} and {@link MultiSelectEntityParameter} produce incompatible value types (a single
 * entity vs. a list of entities) and therefore cannot share a common base class. Hence, the shared logic
 * (autocompleter resolution, descriptor and mapper access, entity lookups and tenant checks) is provided via
 * this support class which both parameter types delegate to.
 * <p>
 * The parameter callbacks are passed in as {@link Supplier suppliers} (i.e. method references onto the
 * parameter itself) so that overrides in parameter subclasses are properly picked up.
 *
 * @param <V> the type of entities handled by the parameter
 */
public class EntityParameterProxy<V extends BaseEntity<?>> {

    @Part
    private static Mixing mixing;

    @Part
    @Nullable
    private static Tenants<?, ?, ?> tenants;

    @Part
    private static GlobalContext globalContext;

    /**
     * Provides the type of entities handled by the parameter.
     */
    private final Supplier<Class<V>> typeSupplier;

    /**
     * Provides the {@link Autocompleter} type used to compute suggestions (may supply <tt>null</tt>).
     */
    private final Supplier<Class<? extends Autocompleter<V>>> autocompleterSupplier;

    /**
     * Provides the custom autocomplete URI used if no {@link Autocompleter} is configured (may supply
     * <tt>null</tt>).
     */
    private final Supplier<String> customAutocompleteUriSupplier;

    /**
     * Provides the label of the parameter, used in error messages.
     */
    private final Supplier<String> labelSupplier;

    /**
     * Caches the {@link EntityDescriptor} of the entity type as determined by {@link #getDescriptor()}.
     */
    private EntityDescriptor descriptor;

    /**
     * Creates a new support instance using the given parameter callbacks.
     *
     * @param typeSupplier                  provides the type of entities handled by the parameter
     * @param autocompleterSupplier         provides the {@link Autocompleter} type to use (may supply <tt>null</tt>)
     * @param customAutocompleteUriSupplier provides the custom autocomplete URI to use (may supply <tt>null</tt>)
     * @param labelSupplier                 provides the label of the parameter (used in error messages)
     */
    public EntityParameterProxy(Supplier<Class<V>> typeSupplier,
                                Supplier<Class<? extends Autocompleter<V>>> autocompleterSupplier,
                                Supplier<String> customAutocompleteUriSupplier,
                                Supplier<String> labelSupplier) {
        this.typeSupplier = typeSupplier;
        this.autocompleterSupplier = autocompleterSupplier;
        this.customAutocompleteUriSupplier = customAutocompleteUriSupplier;
        this.labelSupplier = labelSupplier;
    }

    /**
     * Determines the registered {@link Autocompleter} instance for the parameter (if one is configured).
     *
     * @return the autocompleter to use wrapped as optional or an empty optional if none is configured or registered
     */
    @SuppressWarnings("unchecked")
    public Optional<? extends Autocompleter<V>> findAutocompleter() {
        return Optional.ofNullable(autocompleterSupplier.get())
                       .flatMap(type -> globalContext.getParts(Autocompleter.class)
                                                     .stream()
                                                     .filter(type::isInstance)
                                                     .map(autocompleter -> (Autocompleter<V>) autocompleter)
                                                     .findFirst());
    }

    /**
     * Returns the autocompletion URL used to determine suggestions for inputs provided by the user.
     *
     * @return the autocomplete URL used to provide suggestions for user input or <tt>null</tt> if neither an
     * {@link Autocompleter} nor a custom autocomplete URI is available
     */
    @Nullable
    public String determineAutocompleteUrl() {
        if (autocompleterSupplier.get() == null) {
            return customAutocompleteUriSupplier.get();
        }

        return findAutocompleter().map(Autocompleter::getName)
                                  .map(name -> "/jobs/parameter-autocomplete/" + name)
                                  .orElseGet(this::handleUnregisteredAutocompleter);
    }

    /**
     * Reports a configured but unregistered {@link Autocompleter} and provides the fallback URI.
     * <p>
     * The misconfiguration is reported as incident instead of silently degrading into a select without
     * suggestions.
     *
     * @return the custom autocomplete URI as fallback (which is most probably <tt>null</tt>)
     */
    @Nullable
    private String handleUnregisteredAutocompleter() {
        Exceptions.handle()
                  .withSystemErrorMessage(
                          "The autocompleter %s used by the job parameter '%s' is not registered as part!",
                          autocompleterSupplier.get().getName(),
                          labelSupplier.get())
                  .handle();

        return customAutocompleteUriSupplier.get();
    }

    /**
     * Determines the {@link EntityDescriptor} for the type of entities handled by the parameter.
     *
     * @return the entity descriptor for the parameter type
     */
    public EntityDescriptor getDescriptor() {
        if (descriptor == null) {
            descriptor = mixing.getDescriptor(typeSupplier.get());
        }

        return descriptor;
    }

    /**
     * Returns the mapper which is to be used for the entities handled by the parameter.
     *
     * @return the mapper of the represented entity type
     */
    public BaseMapper<V, ?, ?> getMapper() {
        return getDescriptor().getMapper();
    }

    /**
     * Resolves the given input (an entity id) into an entity.
     *
     * @param input the entity id wrapped as <tt>Value</tt>
     * @return the resolved entity wrapped as optional or an empty optional if the entity couldn't be resolved
     */
    public Optional<V> findEntity(Value input) {
        return getMapper().find(typeSupplier.get(), input.get());
    }

    /**
     * Resolves the given input (an entity id) into an entity and fails if a non-empty input cannot be resolved.
     *
     * @param input the entity id wrapped as <tt>Value</tt>
     * @return the resolved entity or <tt>null</tt> if the input was empty
     * @throws sirius.kernel.health.HandledException if a non-empty input doesn't resolve into an entity
     */
    @Nullable
    public V findEntityOrFail(Value input) {
        V entity = findEntity(input).orElse(null);
        if (entity == null) {
            if (input.isFilled()) {
                throw Exceptions.createHandled()
                                .withNLSKey("Parameter.invalidValue")
                                .set("name", labelSupplier.get())
                                .set("message", NLS.get("EntityParameter.mustExist"))
                                .handle();
            }
            return null;
        }

        return entity;
    }

    /**
     * Derives a label to show for a given entity.
     *
     * @param entity the entity to derive the label from
     * @return the label or textual representation to use for the given entity
     */
    public String createLabel(V entity) {
        return findAutocompleter().map(autocompleter -> autocompleter.toLabel(entity)).orElseGet(entity::toString);
    }

    /**
     * Asserts that the given entity belongs to the current tenant (in case the entity is tenant aware and the
     * tenants framework is active).
     *
     * @param entity the entity to check
     */
    public void assertTenantAccess(V entity) {
        if (tenants != null && entity instanceof TenantAware tenantAware) {
            tenants.assertTenant(tenantAware);
        }
    }
}
