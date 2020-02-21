/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jdbc;

import com.alibaba.fastjson.JSON;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.es.ElasticEntity;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mixing;
import sirius.db.mixing.query.Query;
import sirius.db.mongo.MongoEntity;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Counter;
import sirius.web.controller.AutocompleteHelper;
import sirius.web.controller.BasicController;
import sirius.web.controller.Controller;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 Provides a query GUI for all entities managed by <tt>Mixing</tt>.
 */
@Register(classes = Controller.class)
public class QueryController extends BasicController {

    @Part
    private Mixing mixing;

    /**
     * Builds the given query via {@link Query#queryString}, executes it and renders the UI.
     *
     * @param webContext the context containing the query
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    @Routed("/system/query")
    public void query(WebContext webContext) {
        String className = webContext.get("class").asString();
        String queryString = webContext.get("query").asString();
        int limit = webContext.get("limit").asInt(10);

        StringBuilder sourceBuilder = new StringBuilder();
        Counter counter = new Counter();

        if (Strings.isFilled(className)) {
            EntityDescriptor descriptor = mixing.getDescriptor(className.toUpperCase());
            Class<? extends BaseEntity> type = descriptor.getType().asSubclass(BaseEntity.class);
            Query<?, ?, ?> query = descriptor.getMapper().select(type).queryString(queryString);

            sourceBuilder.append("/* Number of results: ").append(query.count()).append(" */\n\n");

            ((Query<?, ?, ?>) query.limit(limit)).iterateAll(entity -> addEntity(sourceBuilder, entity, counter));
        }

        webContext.respondWith()
                  .template("/templates/biz/model/query.html.pasta",
                            getAvailableEntityTypes(),
                            className,
                            queryString,
                            sourceBuilder.toString(),
                            limit);
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
            for (Class<?> type : getAvailableEntityTypes()) {
                String displayableName = getDisplayableName(type);
                if (displayableName.toLowerCase().contains(query.toLowerCase())) {
                    result.accept(new AutocompleteHelper.Completion(type.getSimpleName(), displayableName));
                }
            }
        }));
    }

    private <E extends BaseEntity<?>> void addEntity(StringBuilder sourceBuilder, E entity, Counter counter) {
        sourceBuilder.append("/* ").append(counter.inc()).append(" */").append("\n");

        Map<String, Object> properties = new TreeMap<>();
        mixing.getDescriptor(entity.getClass()).getProperties().forEach(property -> {
            String name = property.getName();
            Object value = property.getValue(entity);

            properties.put(name, value);
        });

        sourceBuilder.append(JSON.toJSONString(properties, true));
        sourceBuilder.append("\n\n");
    }

    private List<Class<?>> getAvailableEntityTypes() {
        return mixing.getDescriptors()
                     .stream()
                     .filter(descriptor -> MongoEntity.class.isAssignableFrom(descriptor.getType())
                                           || ElasticEntity.class.isAssignableFrom(descriptor.getType()))
                     .map(EntityDescriptor::getType)
                     .sorted(Comparator.comparing(Class::getSimpleName))
                     .collect(Collectors.toList());
    }

    /**
     * Used to add the type of the entity to the label during autocompletion.
     *
     * @param clazz the class corresponding to database entities
     * @return the class name + the type
     */
    private String getDisplayableName(Class<?> clazz) {
        String type;

        if (MongoEntity.class.isAssignableFrom(clazz)) {
            type = "MongoDB";
        } else if (ElasticEntity.class.isAssignableFrom(clazz)) {
            type = "ElasticSearch";
        } else {
            type = "Unknown";
        }

        return clazz.getSimpleName() + " (" + type + ")";
    }
}
