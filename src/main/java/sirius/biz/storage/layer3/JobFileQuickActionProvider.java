/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.biz.jobs.JobFactory;
import sirius.biz.tycho.QuickAction;
import sirius.kernel.di.std.PriorityParts;
import sirius.kernel.di.std.Register;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Provides job based quick actions for files. (Short-cut to start a job for a given file).
 */
@Register
public class JobFileQuickActionProvider implements FileQuickActionProvider {

    /**
     * Defines the permission required to execute jobs.
     */
    public static final String PERMISSION_EXECUTE_JOBS = "permission-execute-jobs";

    @PriorityParts(JobFactory.class)
    private List<JobFactory> factories;

    /**
     * Returns a stream of {@link JobFactory jobs} available for the current user.
     *
     * @return a stream of jobs which are available to the current user and match the given search query
     */
    public Stream<JobFactory> getAvailableJobs() {
        UserInfo currentUser = UserContext.getCurrentUser();
        if (!currentUser.hasPermission(PERMISSION_EXECUTE_JOBS)) {
            return Stream.empty();
        }

        Stream<JobFactory> stream = factories.stream().filter(JobFactory::isAccessibleToCurrentUser);
        stream = stream.sorted(Comparator.comparingInt(JobFactory::getPriority).thenComparing(JobFactory::getLabel));

        return stream;
    }

    @Override
    public void computeQuickAction(VirtualFile virtualFile, Consumer<QuickAction> consumer) {
        String uri = WebContext.getCurrent().getRequest().uri();
        getAvailableJobs().filter(jobFactory -> jobFactory.generatePresetUrl(uri, virtualFile) != null)
                          .forEach(jobFactory -> consumer.accept(new QuickAction().withIcon("fa-solid fa-cogs")
                                                                                  .withLabel(jobFactory.getLabel())
                                                                                  .withUrl(jobFactory.generatePresetUrl(
                                                                                          uri,
                                                                                          virtualFile))));
    }

    @Override
    public int getPriority() {
        return 90;
    }
}
