/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.PriorityParts;
import sirius.kernel.di.std.Register;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Provides a central place to find all available jobs.
 */
@Register(classes = Jobs.class)
public class Jobs {

    /**
     * Names the framework which must be enabled to activate the jobs feature.
     */
    public static final String FRAMEWORK_JOBS = "biz.jobs";

    /**
     * Defines the permission required to execute jobs.
     */
    public static final String PERMISSION_EXECUTE_JOBS = "permission-execute-jobs";

    @Part
    private GlobalContext ctx;

    @PriorityParts(JobFactory.class)
    private List<JobFactory> factories;

    /**
     * Returns a stream of {@link JobFactory jobs} available for the current user.
     *
     * @param query the search query to filter by
     * @return a stream of jobs which are available to the current user and match the given search query
     */
    public Stream<JobFactory> getAvailableJobs(@Nullable String query) {
        UserInfo currentUser = UserContext.getCurrentUser();
        if (!currentUser.hasPermission(PERMISSION_EXECUTE_JOBS)) {
            return Stream.empty();
        }

        Stream<JobFactory> stream = factories.stream().filter(JobFactory::isAccessibleToCurrentUser);

        if (Strings.isFilled(query)) {
            String queryAsLowerCase = query.toLowerCase();
            stream = stream.filter(factory -> factory.getLabel().toLowerCase().contains(queryAsLowerCase));
        }

        stream = stream.sorted(Comparator.comparingInt(JobFactory::getPriority).thenComparing(JobFactory::getLabel));

        return stream;
    }

    /**
     * Returns a jobs which can provide a preset URL for the given target object.
     *
     * @param uri          a target URI for which matching jobs should be determined.
     *                     This is most probably the URL of the page which tries to determine if there are matching jobs as
     *                     might look like <tt>/foo/bar?param=value</tt>
     * @param targetObject the optional target object which is being shown / processed / edited by the page
     * @return a list of tuples containing the preset URL associated job for the given object
     */
    public List<Tuple<String, JobFactory>> getMatchingInteractiveJobs(@Nonnull String uri, Object targetObject) {
        return getAvailableJobs(null).filter(JobFactory::canStartInteractive)
                                     .map(job -> Tuple.create(job.generatePresetUrl(uri, targetObject), job))
                                     .filter(tuple -> tuple.getFirst() != null)
                                     .toList();
    }

    /**
     * Resolves the job factory with the given name.
     * <p>
     * Note that this ensures, that the current user may invoke jobs at all, but it will not check the permissions of
     * the factory itself.
     *
     * @param name         the name of the job factory to resolve
     * @param expectedType a type to cast the job factory to
     * @param <J>          the generic version of <tt>expectedType</tt>
     * @return the job factory with the given name, cast to the given class
     */
    @SuppressWarnings("unchecked")
    public <J extends JobFactory> J findFactory(String name, Class<J> expectedType) {
        UserInfo currentUser = UserContext.getCurrentUser();
        currentUser.assertPermission(PERMISSION_EXECUTE_JOBS);

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

    /**
     * Tries to resolve the job factory with the given name.
     * <p>
     * Note that this method does not check if the current user may invoke this job at all. {@linkplain
     * Optional#filter(Predicate) Filter} the returned optional with
     * {@link JobFactory#isAccessibleToCurrentUser()} if you need to check this.
     *
     * @param name the name of the job factory to resolve
     * @return the job factory with the given name or an empty optional if no such factory exists
     */
    public Optional<JobFactory> tryFindFactory(String name) {
        return tryFindFactory(name, JobFactory.class);
    }

    /**
     * Tries to resolve the job factory with the given name and class.
     * <p>
     * Note that this method does not check if the current user may invoke this job at all. {@linkplain
     * Optional#filter(Predicate) Filter} the returned optional with
     * {@link JobFactory#isAccessibleToCurrentUser()} if you need to check this.
     *
     * @param name         the name of the job factory to resolve
     * @param expectedType a type to cast the job factory to
     * @param <J>          the generic value of <tt>expectedType</tt>
     * @return the job factory with the given name or an empty optional if no such factory exists
     */
    public <J extends JobFactory> Optional<J> tryFindFactory(String name, Class<J> expectedType) {
        try {
            return Optional.ofNullable(findFactory(name, expectedType));
        } catch (Exception _) {
            return Optional.empty();
        }
    }
}
