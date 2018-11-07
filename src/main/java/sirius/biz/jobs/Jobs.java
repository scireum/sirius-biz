/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.PriorityParts;
import sirius.kernel.di.std.Register;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Register(classes = Jobs.class)
public class Jobs {

    @Part
    private GlobalContext ctx;

    @PriorityParts(JobFactory.class)
    private List<JobFactory> factories;

    public Stream<JobFactory> getAvailableJobs(@Nullable String query) {
        UserInfo currentUser = UserContext.getCurrentUser();

        return factories.stream()
                        .filter(factory -> factory.getRequiredPermissions()
                                                  .stream()
                                                  .allMatch(currentUser::hasPermission))
                        .filter(factory -> Strings.isEmpty(query) || factory.getLabel().contains(query));
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
