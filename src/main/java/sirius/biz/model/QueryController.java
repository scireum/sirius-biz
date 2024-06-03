/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.biz.tenants.TenantUserManager;
import sirius.biz.web.BizController;
import sirius.db.es.ElasticEntity;
import sirius.db.es.ElasticQuery;
import sirius.db.es.annotations.IndexMode;
import sirius.db.jdbc.SQLEntity;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Property;
import sirius.db.mixing.properties.AmountProperty;
import sirius.db.mixing.query.Query;
import sirius.db.mixing.query.constraints.Constraint;
import sirius.db.mongo.MongoEntity;
import sirius.db.mongo.MongoQuery;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Amount;
import sirius.kernel.commons.NumberFormat;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Register;
import sirius.web.controller.AutocompleteHelper;
import sirius.web.controller.Message;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;
import sirius.web.services.InternalService;
import sirius.web.services.JSONStructuredOutput;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Provides a query GUI for all entities managed by <tt>Mixing</tt>.
 */
@Register
public class QueryController extends BizController {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    @ConfigValue("security.query-api.enabled")
    private boolean queryApiEnabled;

    /**
     * Builds the given query via {@link Query#queryString}, executes it and renders the UI.
     *
     * @param webContext the context containing the query
     */

    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    @Routed("/system/query")
    public void query(WebContext webContext) {
        EntityDescriptor descriptor = mixing.findDescriptor(webContext.get("class").asString()).orElse(null);
        String queryString = webContext.get("query").asString();
        int limit = Math.min(webContext.get("limit").asInt(DEFAULT_LIMIT), MAX_LIMIT);

        if (descriptor != null) {
            List<BaseEntity<?>> entities = executeQuery(descriptor, queryString, limit);
            webContext.respondWith()
                      .template("/templates/biz/model/query-results.html.pasta",
                                descriptor,
                                queryString,
                                limit,
                                entities,
                                determineVisibleProperties(descriptor));
        } else {
            webContext.respondWith()
                      .template("/templates/biz/model/query-descriptors.html.pasta",
                                fetchRelevantDescriptors().toList(),
                                descriptor,
                                queryString,
                                limit);
        }
    }

    @SuppressWarnings("unchecked")
    private <C extends Constraint, Q extends Query<Q, ?, C>> List<BaseEntity<?>> executeQuery(EntityDescriptor descriptor,
                                                                                              String queryString,
                                                                                              int limit) {
        try {
            // As Query is a self-referential type, we have to use a custom generic type here...
            Q baseQuery = (Q) descriptor.getMapper().select((Class<BaseEntity<?>>) descriptor.getType());

            // Compile the query string and extract the debug flag...
            Tuple<Constraint, Boolean> constraintAndDebugFlag =
                    descriptor.getMapper().filters().compileString(descriptor, queryString, Collections.emptyList());
            baseQuery.where((C) constraintAndDebugFlag.getFirst());

            // Log effective query if desired...
            if (Boolean.TRUE.equals(constraintAndDebugFlag.getSecond())) {
                UserContext.message(Message.info().withTextMessage("Effective Query: " + baseQuery));
            }

            // Elastic entities might be routed - we ignore this here and access all shards anyway...
            if (baseQuery instanceof ElasticQuery) {
                ((ElasticQuery<?>) baseQuery).deliberatelyUnrouted();
            }

            long numberOfEntities = getTotalCount(limit, baseQuery);

            // Actually perform the query...
            List<BaseEntity<?>> result = new ArrayList<>();
            if (numberOfEntities > 0) {
                Watch watch = Watch.start();
                baseQuery.limit(limit).iterateAll(result::add);

                UserContext.message(Message.info()
                                           .withTextMessage(Strings.apply("Showing %s of %s results - Query took %sms",
                                                                          Amount.of(result.size()).toRoundedString(),
                                                                          Amount.of(numberOfEntities).toRoundedString(),
                                                                          watch.elapsedMillis())));
            }

            return result;
        } catch (IllegalArgumentException exception) {
            // The QueryCompiler generates an IllegalArgumentException for invalid fields and tokens.
            // In our case we don't want to write them into the syslog but just output the message...
            UserContext.message(Message.error().withTextMessage(exception.getMessage()));
        } catch (Exception exception) {
            handle(exception);
        }

        return Collections.emptyList();
    }

    private <C extends Constraint, Q extends Query<Q, ?, C>> long getTotalCount(int limit, Q baseQuery) {
        if (baseQuery instanceof MongoQuery) {
            Optional<Long> count = ((MongoQuery<?>) baseQuery).count(false, 5000);

            if (count.isPresent()) {
                return count.get();
            }
            UserContext.message(Message.warn()
                                       .withTextMessage(Strings.apply("Fetching total result count timed out.")));
            return limit;
        }

        return baseQuery.count();
    }

    @Nonnull
    protected Stream<EntityDescriptor> fetchRelevantDescriptors() {
        return mixing.getDescriptors()
                     .stream()
                     .filter(descriptor -> BaseEntity.class.isAssignableFrom(descriptor.getType()))
                     .sorted(Comparator.comparing(EntityDescriptor::getName));
    }

    private List<Property> determineVisibleProperties(EntityDescriptor descriptor) {
        return descriptor.getProperties()
                         .stream()
                         .filter(property -> !BaseEntity.ID.getName().equals(property.getName()))
                         .filter(property -> !property.getAnnotation(IndexMode.class)
                                                      .map(IndexMode::excludeFromSource)
                                                      .orElse(false))
                         .sorted(Comparator.comparing(Property::getName))
                         .toList();
    }

    /**
     * Provides a JSON based query facility.
     * <p>
     * Note that this is only available in TEST and DEV systems as it is only intended to be used for integration tests.
     * <p>
     * Just like the UI, this expects <tt>type</tt> to contain a Mixing descriptor name as produced by
     * {@link sirius.db.mixing.Mixing#getNameForType(Class)} and <tt>query</tt> to contain a proper query
     * e.g. "id:X". Additionally <tt>limit</tt> can specify how many entities are to be returned.
     * <p>
     * Note that this isn't a public API as it isn't available on production systems.
     *
     * @param webContext the request to handle
     * @param output     the output to write the retrieved entities to
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    @Routed("/system/query/api")
    @InternalService
    public void queryApi(WebContext webContext, JSONStructuredOutput output) {
        if (!queryApiEnabled) {
            throw new IllegalStateException("The query API is not enabled in this system.");
        }
        if (Sirius.isProd()) {
            throw new IllegalStateException("The query API is not available in productive systems.");
        }

        EntityDescriptor descriptor = mixing.findDescriptor(webContext.get("type").asString())
                                            .orElseThrow(() -> new IllegalArgumentException(
                                                    "Please provide a valid class parameter."));
        String queryString = webContext.get("query").asString();
        int limit = Math.min(webContext.get("limit").asInt(DEFAULT_LIMIT), MAX_LIMIT);
        List<BaseEntity<?>> entities = executeQuery(descriptor, queryString, limit);

        output.beginArray("entities");
        for (BaseEntity<?> entity : entities) {
            output.beginObject("entity");
            output.property("id", entity.getId());
            for (Property property : determineVisibleProperties(descriptor)) {
                if (property.is(AmountProperty.class)) {
                    output.amountProperty(property.getName(),
                                          Value.of(property.getValue(entity)).getAmount(),
                                          NumberFormat.MACHINE_FIVE_DECIMAL_PLACES,
                                          true);
                } else {
                    output.property(property.getName(), property.getValue(entity));
                }
            }
            output.endObject();
        }
        output.endArray();
    }

    /**
     * Provides suggestions for available database entities based on the query.
     *
     * @param webContext the current web context containing the query
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    @Routed("/system/query/autocomplete")
    public void entityTypeAutocomplete(WebContext webContext) {
        AutocompleteHelper.handle(webContext, ((query, result) -> {
            String effectiveQuery = query.toLowerCase();
            fetchRelevantDescriptors().filter(descriptor -> fuzzyMatches(effectiveQuery, descriptor.getName())
                                                            || fuzzyMatches(effectiveQuery,
                                                                            descriptor.getType().getSimpleName()))
                                      .map(this::createCompletion)
                                      .limit(AutocompleteHelper.DEFAULT_LIMIT)
                                      .forEach(result);
        }));
    }

    /**
     * Checks if the given query is contained in the searched value.
     * <p>
     * This checks if the characters of the query are contained in the searched value in the correct order.
     * It also ignores casing.
     *
     * @param effectiveQuery the lower-cased query text to search for
     * @param searchedValue  the value to search in
     * @return <tt>true</tt> if the query is contained in the searched value
     */
    private boolean fuzzyMatches(String effectiveQuery, String searchedValue) {
        String effectiveSearchedValue = searchedValue.toLowerCase();
        int queryIndex = 0;
        // Iterate over the characters of the searched value.
        // Break if we reached the end of the query or searched value.
        for (int targetIndex = 0;
             targetIndex < effectiveSearchedValue.length() && queryIndex < effectiveQuery.length();
             targetIndex++) {
            if (effectiveQuery.charAt(queryIndex) == effectiveSearchedValue.charAt(targetIndex)) {
                // If the current character matches, we move to the next character in the query
                queryIndex++;
            }
        }
        // If we reached the end of the query, we found a match
        return queryIndex == effectiveQuery.length();
    }

    private AutocompleteHelper.Completion createCompletion(EntityDescriptor descriptor) {
        return AutocompleteHelper.suggest(descriptor.getName()).withFieldLabel(descriptor.getType().getSimpleName());
    }

    /**
     * Determines the type name to use for an entity in <tt>/system/query</tt>.
     *
     * @param descriptor the entity descriptor
     * @return a representative name for the database technology being used
     */
    public static String determineEntityType(EntityDescriptor descriptor) {
        if (SQLEntity.class.isAssignableFrom(descriptor.getType())) {
            return "SQL";
        } else if (MongoEntity.class.isAssignableFrom(descriptor.getType())) {
            return "MongoDB";
        } else if (ElasticEntity.class.isAssignableFrom(descriptor.getType())) {
            return "Elastic";
        } else {
            return "";
        }
    }

    /**
     * Determines a color to use for the entity type tag <tt>/system/query</tt>.
     * <p>
     * Note that this is just a visual cue and not a truly computed value
     *
     * @param descriptor the entity descriptor
     * @return a color to use for the database type tag/dot
     */
    public static String determineEntityColor(EntityDescriptor descriptor) {
        if (SQLEntity.class.isAssignableFrom(descriptor.getType())) {
            return "blue";
        } else if (MongoEntity.class.isAssignableFrom(descriptor.getType())) {
            return "green";
        } else if (ElasticEntity.class.isAssignableFrom(descriptor.getType())) {
            return "yellow-dark";
        } else {
            return "";
        }
    }

    /**
     * Computes (or rather guesses) a color to use for an enum value in <tt>/system/query</tt>.
     * <p>
     * Note that this is more of a visual cue, than a truly computed value. The idea is, that the first enum
     * value is (in most cases) a special / default one. Therefore, we use distinct colors for the first two
     * constants and color all others in the same (third).
     *
     * @param enumValue the enum to color
     * @return a color to use for the enum value
     */
    public static String determineEnumTagColor(Enum<?> enumValue) {
        if (enumValue == null) {
            return "gray";
        }

        return switch (enumValue.ordinal()) {
            case 0 -> "blue";
            case 1 -> "green";
            default -> "violet-light";
        };
    }
}
