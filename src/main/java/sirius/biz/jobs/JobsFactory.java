/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import sirius.kernel.di.std.Named;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Created by aha on 22.07.16.
 */
public interface JobsFactory extends Named {

    void collectJobs(@Nullable String query, Consumer<JobDescription> jobsCollector);

    JobDescription resolve(String name);
}
