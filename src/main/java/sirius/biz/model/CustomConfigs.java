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
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Composite;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mapping;
import sirius.kernel.commons.Strings;
import sirius.kernel.health.Exceptions;

import java.util.Optional;

/**
 * Validates the custom HOCON configurations which are stored in entities via {@link PermissionData} or
 * {@link TenantData}.
 * <p>
 * Such configs are parsed at runtime whenever the settings of the associated scope, tenant or user are accessed. An
 * invalid config therefore renders the whole scope unusable. This class ensures that no invalid config can be stored.
 */
public final class CustomConfigs {

    private CustomConfigs() {
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
}
