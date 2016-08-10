/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.system;

import sirius.biz.jobs.JobDescription;
import sirius.kernel.nls.NLS;
import sirius.web.security.Permissions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Created by aha on 22.07.16.
 */
public abstract class SystemJobDescription extends JobDescription {

    protected final Set<String> requiredPermissions = Permissions.computePermissionsFromAnnotations(getClass());

    @Nonnull
    @Override
    public String getTitle() {
        return NLS.get("Job." + getName());
    }

    @Nullable
    @Override
    public String getDescription() {
        return NLS.getIfExists("Job." + getName() + ".help", NLS.getCurrentLang()).orElse(null);
    }

    @Nonnull
    @Override
    public String getFactory() {
        return SystemJobsFactory.SYSTEM;
    }

    @Override
    public void collectPermissions(Consumer<String> permissions) {
        requiredPermissions.forEach(permissions);
    }
}
