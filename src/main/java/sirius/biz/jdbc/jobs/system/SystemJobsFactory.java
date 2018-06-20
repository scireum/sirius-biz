/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jdbc.jobs.system;

import sirius.biz.jdbc.jobs.JobDescription;
import sirius.biz.jdbc.jobs.JobsFactory;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Parts;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Created by aha on 22.07.16.
 */
@Register
public class SystemJobsFactory implements JobsFactory {

    public static final String SYSTEM = "system";

    @Parts(SystemJobDescription.class)
    private Collection<SystemJobDescription> jobs;

    @Part
    private GlobalContext ctx;

    @Override
    public void collectJobs(@Nullable String query, Consumer<JobDescription> jobsCollector) {
        Stream<SystemJobDescription> stream = jobs.stream();
        if (Strings.isFilled(query)) {
            String filterQuery = query.trim().toLowerCase();
            stream = stream.filter(j -> j.getTitle().contains(filterQuery));
        }
        stream.forEach(jobsCollector);
    }

    @Override
    public JobDescription resolve(String name) {
        return ctx.findPart(name, SystemJobDescription.class);
    }

    @Nonnull
    @Override
    public String getName() {
        return SYSTEM;
    }
}
