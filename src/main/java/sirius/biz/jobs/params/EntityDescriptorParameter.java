/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Provides a parameter which accepts {@link EntityDescriptor}.
 */
public class EntityDescriptorParameter extends Parameter<EntityDescriptor, EntityDescriptorParameter> {

    @Part
    protected static Mixing mixing;

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    public EntityDescriptorParameter(String name, String label) {
        super(name, label);
    }

    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/params/entity-descriptors.html.pasta";
    }

    /**
     * Enumerates all {@link EntityDescriptor descriptors} known to the system.
     *
     * @return the list of value defined by the enum type
     */
    public List<String> getValues() {
        return mixing.getDesciptors()
                     .stream()
                     .map(entityDescriptor -> Mixing.getNameForType(entityDescriptor.getType()))
                     .collect(Collectors.toList());
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        if (input.isEmptyString()) {
            return null;
        }

        EntityDescriptor ed = mixing.getDescriptor(input.getString());

        if (ed == null) {
            return null;
        }

        return input.getString();
    }

    @Override
    protected Optional<EntityDescriptor> resolveFromString(Value input) {
        if (input.isEmptyString()) {
            return Optional.empty();
        }
        EntityDescriptor ed = mixing.getDescriptor(input.getString());
        return Optional.ofNullable(ed);
    }
}
