/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import sirius.kernel.async.Tasks;
import sirius.kernel.commons.Context;
import sirius.kernel.di.std.Named;
import sirius.kernel.di.std.Priorized;
import sirius.web.tasks.ManagedTaskContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Created by aha on 22.07.16.
 */
public abstract class JobDescription implements Priorized, Named {

    @Nonnull
    public abstract String getFactory();

    @Nonnull
    public abstract String getTitle();

    public String getTaskTitle(Context parameters) {
        return getTitle();
    }

    @Override
    public int getPriority() {
        return Priorized.DEFAULT_PRIORITY;
    }

    @Nullable
    public String getDescription() {
        return null;
    }

    public abstract void collectPermissions(Consumer<String> permissions);

    public abstract void collectParameters(Consumer<JobParameterDescription> parameterCollector);

    public abstract boolean verifyParameters(Context parameters);

    @Nonnull
    public String getPreferredExecutor() {
        return Tasks.DEFAULT;
    }

    public abstract void execute(Context parameters, ManagedTaskContext task);
}
