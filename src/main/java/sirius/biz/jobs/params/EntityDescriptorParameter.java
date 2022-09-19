/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.db.es.ElasticEntity;
import sirius.db.jdbc.SQLEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;
import sirius.db.mongo.MongoEntity;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Provides a parameter which accepts {@link EntityDescriptor}.
 */
public class EntityDescriptorParameter extends ParameterBuilder<EntityDescriptor, EntityDescriptorParameter> {

    @Part
    protected static Mixing mixing;

    private Predicate<EntityDescriptor> filter;

    /**
     * Creates a new parameter with a default name and label.
     */
    public EntityDescriptorParameter() {
        super("descriptor", "Type");
    }

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    public EntityDescriptorParameter(String name, String label) {
        super(name, label);
    }

    /**
     * Provides a filter to only select entities stored in <tt>Elasticsearch</tt>.
     *
     * @param descriptor the descriptor to filter
     * @return <tt>true</tt> if the associated entities are stored in ES, <tt>false</tt> otherwise
     * @see #withFilter(Predicate)
     */
    public static boolean isElasticEntity(EntityDescriptor descriptor) {
        return ElasticEntity.class.isAssignableFrom(descriptor.getType());
    }

    /**
     * Provides a filter to only select entities stored in a <tt>JDBC</tt> database.
     *
     * @param descriptor the descriptor to filter
     * @return <tt>true</tt> if the associated entities are stored in a JDBC database, <tt>false</tt> otherwise
     * @see #withFilter(Predicate)
     */
    public static boolean isSQLEntity(EntityDescriptor descriptor) {
        return SQLEntity.class.isAssignableFrom(descriptor.getType());
    }

    /**
     * Provides a filter to only select entities stored in <tt>MongoDB</tt>.
     *
     * @param descriptor the descriptor to filter
     * @return <tt>true</tt> if the associated entities are stored in MongoDB, <tt>false</tt> otherwise
     * @see #withFilter(Predicate)
     */
    public static boolean isMongoEntity(EntityDescriptor descriptor) {
        return MongoEntity.class.isAssignableFrom(descriptor.getType());
    }

    /**
     * Applies a filter used to determine which descriptors can be selected.
     *
     * @param filter the filter to apply
     * @return the parameter itself for fluent method calls
     */
    public EntityDescriptorParameter withFilter(Predicate<EntityDescriptor> filter) {
        this.filter = filter;
        return self();
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
    public List<EntityDescriptor> getValues() {
        Stream<EntityDescriptor> descriptors = mixing.getDescriptors().stream();
        if (filter != null) {
            descriptors = descriptors.filter(filter);
        }

        return descriptors.toList();
    }

    /**
     * Used by the template to determine the unique type name to later resolve the descriptor using {@link Mixing#getDescriptor(String)}.
     *
     * @param descriptor the descriptor to get the name for
     * @return the unique type name of the descriptor
     */
    public String getLookupName(EntityDescriptor descriptor) {
        return Mixing.getNameForType(descriptor.getType());
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
    public Optional<?> computeValueUpdate(Map<String, String> parameterContext) {
        return updater.apply(parameterContext)
                      .map(value -> Map.of("value", getLookupName(value), "text", value.getType().getSimpleName()));
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
