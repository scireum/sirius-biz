/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import sirius.biz.tycho.search.OpenSearchProvider;
import sirius.biz.tycho.search.OpenSearchResult;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Makes {@link JobFactory jobs} visible to the {@link sirius.biz.tycho.search.OpenSearchController}.
 */
@Register(framework = Jobs.FRAMEWORK_JOBS)
public class JobsSearchProvider implements OpenSearchProvider {

    @Part
    private Jobs jobs;

    @Override
    public String getLabel() {
        return NLS.get("JobFactory.plural");
    }

    @Nullable
    @Override
    public String getUrl() {
        return "/jobs";
    }

    @Override
    public String getIcon() {
        return "fa-gears";
    }

    @Override
    public boolean ensureAccess() {
        return true;
    }

    @Override
    public void query(String query, int maxResults, Consumer<OpenSearchResult> resultCollector) {
        jobs.getAvailableJobs(query).filter(JobFactory::canStartInteractive).forEach(jobFactory -> {
            OpenSearchResult result = new OpenSearchResult();
            result.withLabel(jobFactory.getLabel())
                  .withHtmlDescription(jobFactory.getDescription())
                  .withURL("/job/" + jobFactory.getName());
            resultCollector.accept(result);
        });
    }

    @Override
    public int getPriority() {
        return 10;
    }
}
