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
import java.util.Collections;
import java.util.List;
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

    public List<String> getPermissions() {
        return Collections.emptyList();
    }

    public void collectParameters(Consumer<JobParameterDescription> parameterCollector) {
    }

    public boolean verifyParameters(Context parameters) {
        return true;
    }

    @Nonnull
    public String getPreferredExecutor() {
        return Tasks.DEFAULT;
    }

    public boolean isEditable() {
        return false;
    }

    public abstract void execute(Context parameters, ManagedTaskContext task);
}
