/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import sirius.biz.tenants.TenantData;
import sirius.db.es.ElasticQuery;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Composite;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.Property;
import sirius.db.mixing.query.Query;
import sirius.db.mixing.query.constraints.Constraint;
import sirius.kernel.commons.Strings;
import sirius.kernel.health.Exceptions;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Validates the custom HOCON configurations which are stored in entities via {@link PermissionData} or
 * {@link TenantData}.
 * <p>
 * Such configs are parsed at runtime whenever the settings of the associated scope, tenant or user are accessed. An
 * invalid config therefore renders the whole scope unusable. This class ensures that no invalid config can be stored
 * and permits to find configs which became invalid, e.g. because the parser became stricter in a newer version.
 */
public final class CustomConfigs {

    private static final Map<Class<?>, Mapping> CONFIG_FIELDS =
            Map.of(PermissionData.class, PermissionData.CONFIG_STRING, TenantData.class, TenantData.CONFIG_STRING);

    private CustomConfigs() {
    }

    /**
     * Describes a stored config which cannot be parsed.
     *
     * @param entity   the entity which contains the config
     * @param property the property which stores the config
     * @param error    the error reported by the parser, including the affected line
     */
    public record InvalidCustomConfig(BaseEntity<?> entity, Property property, String error) {
    }

    /**
     * Ensures that the given config can be parsed, if it was changed since it was last fetched from the database.
     * <p>
     * Unchanged configs are deliberately not checked, so that entities with a config which became invalid can still
     * be saved (e.g. when updating the last login of a user).
     *
     * @param owner        the entity which contains the composite
     * @param composite    the composite which stores the config
     * @param field        the field of the composite which stores the config
     * @param configString the config to check
     * @throws sirius.kernel.health.HandledException if the config was changed and cannot be parsed
     */
    public static void verifyIfChanged(BaseEntity<?> owner, Composite composite, Mapping field, String configString) {
        if (Strings.isEmpty(configString) || !isChanged(owner, composite, field)) {
            return;
        }

        parse(configString).ifPresent(error -> {
            throw Exceptions.createHandled().withNLSKey("CustomConfigs.invalidConfig").set("error", error).handle();
        });
    }

    private static boolean isChanged(BaseEntity<?> owner, Composite composite, Mapping field) {
        EntityDescriptor descriptor = owner.getDescriptor();
        return descriptor.getProperties()
                         .stream()
                         .filter(property -> field.getName().equals(property.getField().getName()))
                         .filter(property -> property.getTarget(owner) == composite)
                         .anyMatch(property -> descriptor.isChanged(owner, property));
    }

    /**
     * Tries to parse the given config.
     *
     * @param configString the config to parse
     * @return the error reported by the parser or an empty optional if the config is valid
     */
    private static Optional<String> parse(String configString) {
        try {
            ConfigFactory.parseString(configString);
            return Optional.empty();
        } catch (ConfigException exception) {
            return Optional.of(exception.getMessage());
        }
    }

    /**
     * Lists all properties of the given entity type which store a custom config.
     *
     * @param descriptor the entity type to inspect
     * @return all properties which are backed by the config field of {@link PermissionData} or {@link TenantData}
     */
    public static Stream<Property> findConfigProperties(EntityDescriptor descriptor) {
        if (!BaseEntity.class.isAssignableFrom(descriptor.getType())) {
            return Stream.empty();
        }

        return descriptor.getProperties().stream().filter(CustomConfigs::isConfigProperty);
    }

    private static boolean isConfigProperty(Property property) {
        Field field = property.getField();
        Mapping configField = CONFIG_FIELDS.get(field.getDeclaringClass());
        return configField != null && configField.getName().equals(field.getName());
    }

    /**
     * Parses the config stored in the given property of all entities and reports those which are invalid.
     *
     * @param descriptor      the entity type to check
     * @param property        the property which stores the config
     * @param invalidConsumer receives each config which cannot be parsed
     * @return the number of configs which have been checked
     */
    public static long checkStoredConfigs(EntityDescriptor descriptor,
                                          Property property,
                                          Consumer<InvalidCustomConfig> invalidConsumer) {
        return check(descriptor, property, invalidConsumer);
    }

    @SuppressWarnings("unchecked")
    private static <C extends Constraint, Q extends Query<Q, ?, C>> long check(EntityDescriptor descriptor,
                                                                               Property property,
                                                                               Consumer<InvalidCustomConfig> consumer) {
        Q query = (Q) descriptor.getMapper().select((Class<BaseEntity<?>>) descriptor.getType());
        query.where(query.filters().filled(Mapping.named(property.getName())));
        if (query instanceof ElasticQuery<?> elasticQuery) {
            elasticQuery.deliberatelyUnrouted();
        }

        AtomicLong checkedConfigs = new AtomicLong();
        query.streamBlockwise().forEach(entity -> {
            Object configString = property.getValue(entity);
            if (configString instanceof String config && Strings.isFilled(config)) {
                checkedConfigs.incrementAndGet();
                parse(config).ifPresent(error -> consumer.accept(new InvalidCustomConfig(entity, property, error)));
            }
        });

        return checkedConfigs.get();
    }
}
