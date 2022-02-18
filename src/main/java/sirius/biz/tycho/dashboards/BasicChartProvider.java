/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.dashboards;

import sirius.web.security.Permissions;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import java.util.Set;

/**
 * Provides a base implementation of {@link ChartProvider} which takes care of {@link sirius.web.security.Permission}
 * and {@link sirius.web.security.NotPermission} annotations to perform access control.
 */
public abstract class BasicChartProvider implements ChartProvider {

    private Set<String> permissions;

    @Override
    public boolean isAccessible() {
        if (permissions == null) {
            permissions = Permissions.computePermissionsFromAnnotations(getClass());
        }

        UserInfo user = UserContext.getCurrentUser();
        return permissions.stream().allMatch(user::hasPermission);
    }
}
