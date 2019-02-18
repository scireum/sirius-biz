/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.db.es.ElasticEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides a parameter which accepts {@link EntityDescriptor} which represent {@link ElasticEntity elastic entities}.
 */
public class ElasticEntityDescriptorParameter extends EntityDescriptorParameter {

    public ElasticEntityDescriptorParameter(String name, String label) {
        super(name, label);
    }

    /**
     * Enumerates all {@link EntityDescriptor descriptors} which represent {@link ElasticEntity elastic entites} known to the system.
     *
     * @return the list of value defined by the enum type
     */
    public List<String> getValues() {
        return mixing.getDesciptors()
                     .stream()
                     .filter(entityDescriptor -> ElasticEntity.class.isAssignableFrom(entityDescriptor.getType()))
                     .map(entityDescriptor -> Mixing.getNameForType(entityDescriptor.getType()))
                     .collect(Collectors.toList());
    }
}
