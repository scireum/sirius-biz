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
import sirius.db.es.ElasticQuery;
import sirius.db.es.annotations.IndexMode;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Property;
import sirius.db.mixing.query.Query;
import sirius.db.mixing.query.constraints.Constraint;
import sirius.db.mongo.MongoQuery;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Register;
import sirius.web.controller.AutocompleteHelper;
import sirius.web.controller.Message;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides a query GUI for all entities managed by <tt>Mixing</tt>.
 */
@Register
public class QueryController extends BizController {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

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
                                fetchRelevantDescriptors().collect(Collectors.toList()),
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
                UserContext.message(Message.info("Effective Query: " + baseQuery.toString()));
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

                UserContext.message(Message.info(Strings.apply("Showing %s of %s results - Query took %sms",
                                                               result.size(),
                                                               numberOfEntities,
                                                               watch.elapsedMillis())));
            }

            return result;
        } catch (IllegalArgumentException e) {
            // The QueryCompiler generates an IllegalArgumentException for invalid fields and tokens.
            // In our case we don't want to write them into the syslog but just output the message...
            UserContext.message(Message.error(e.getMessage()));
        } catch (Exception e) {
            handle(e);
        }

        return Collections.emptyList();
    }

    private <C extends Constraint, Q extends Query<Q, ?, C>> long getTotalCount(int limit, Q baseQuery) {
        if (baseQuery instanceof MongoQuery) {
            Optional<Long> count = ((MongoQuery<?>) baseQuery).count(false, 5000);

            if (count.isPresent()) {
                return count.get();
            }
            UserContext.message(Message.warn(Strings.apply("Fetching total result count timed out.")));
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
                         .collect(Collectors.toList());
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
            fetchRelevantDescriptors().map(this::createCompletion)
                                      .filter(completion -> filterMatch(effectiveQuery, completion))
                                      .forEach(result);
        }));
    }

    private AutocompleteHelper.Completion createCompletion(EntityDescriptor descriptor) {
        return new AutocompleteHelper.Completion(descriptor.getName(),
                                                 descriptor.getType().getSimpleName(),
                                                 descriptor.getType().getSimpleName());
    }

    private boolean filterMatch(String effectiveQuery, AutocompleteHelper.Completion completion) {
        return completion.getLabel().toLowerCase().contains(effectiveQuery) || completion.getDescription()
                                                                                         .toLowerCase()
                                                                                         .contains(effectiveQuery);
    }
}
