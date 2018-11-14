/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import sirius.kernel.Sirius;
import sirius.kernel.commons.MultiMap;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.PriorityParts;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.di.std.Register;
import sirius.kernel.settings.Extension;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Register(classes = Jobs.class)
public class Jobs {

    //TODO enforce
    public static final String PERMISSION_EXECUTE_JOBS = "permission-execute-jobs";

    @Part
    private GlobalContext ctx;

    @PriorityParts(JobFactory.class)
    private List<JobFactory> factories;

    private Map<String, JobCategory> categories;

    public Map<String, JobCategory> getCategories() {
        if (categories == null) {
            Map<String, JobCategory> result = new HashMap<>();
            for (Extension ext : Sirius.getSettings().getExtensions("jobs.categories")) {
                result.put(ext.getId(),
                           new JobCategory(ext.getId(),
                                           ext.getRaw("label").asString(),
                                           ext.get("icon").asString(),
                                           ext.get("priority").asInt(Priorized.DEFAULT_PRIORITY)));
            }

            categories = result;
        }

        return Collections.unmodifiableMap(categories);
    }

    public Stream<JobFactory> getAvailableJobs(@Nullable String query) {
        UserInfo currentUser = UserContext.getCurrentUser();

        Stream<JobFactory> stream = factories.stream()
                                             .filter(factory -> factory.getRequiredPermissions()
                                                                       .stream()
                                                                       .allMatch(currentUser::hasPermission));
        if (Strings.isFilled(query)) {
            String queryAsLowerCase = query.toLowerCase();
            stream = stream.filter(factory -> factory.getLabel().toLowerCase().contains(queryAsLowerCase));
        }

        return stream;
    }

    public List<Tuple<JobCategory, Collection<JobFactory>>> getAvailableJobsByCategory(@Nullable String query) {
        return groupByCategory(getAvailableJobs(query));
    }

    private List<Tuple<JobCategory, Collection<JobFactory>>> groupByCategory(Stream<JobFactory> jobs) {
        MultiMap<JobCategory, JobFactory> map = MultiMap.createOrdered();
        JobCategory defaultCategory = getCategories().get(JobCategory.CATEGORY_MISC);
        jobs.forEach(job -> {
            map.put(getCategories().getOrDefault(job.getCategory(), defaultCategory), job);
        });

        return map.stream()
                  .map(e -> Tuple.create(e.getKey(), e.getValue()))
                  .sorted(Comparator.comparing(t -> t.getFirst().getPriority()))
                  .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public <J extends JobFactory> J findFactory(String name, Class<J> expectedType) {
        JobFactory result = ctx.getPart(name, JobFactory.class);

        if (result == null) {
            throw new IllegalArgumentException(Strings.apply("No JobFactory found for '%s'", name));
        }
        if (!expectedType.isInstance(result)) {
            throw new IllegalArgumentException(Strings.apply("JobFactory '%s' of type '%s' does not implement '%s'",
                                                             name,
                                                             result.getClass().getName(),
                                                             expectedType.getName()));
        }

        return (J) result;
    }
}
