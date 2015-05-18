/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.web.BizController;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import java.util.Optional;

/**
 * Created by aha on 11.05.15.
 */
@Register(classes = Tenants.class)
public class Tenants {

    public Optional<UserAccount> getCurrentUser() {
        UserInfo user = UserContext.getCurrentUser();
        if (user.isLoggedIn()) {
            return Optional.ofNullable(user.getUserObject(UserAccount.class));
        }

        return Optional.empty();
    }

    public UserAccount getRequiredUser() {
        Optional<UserAccount> ua = getCurrentUser();
        if (ua.isPresent()) {
            return ua.get();
        }
        throw Exceptions.handle()
                        .to(BizController.LOG)
                        .withSystemErrorMessage("A user of type UserAccount was expected but not present!")
                        .handle();
    }

    public boolean hasUser() {
        return getCurrentUser().isPresent();
    }

    public Optional<Tenant> getCurrentTenant() {
        UserInfo user = UserContext.getCurrentUser();
        if (user.isLoggedIn()) {
            UserAccount userObject = user.getUserObject(UserAccount.class);
            if (userObject != null) {
                return Optional.ofNullable(userObject.getTenant().getValue());
            }
        }

        return Optional.empty();
    }

    public Tenant getRequiredTenant() {
        Optional<Tenant> t = getCurrentTenant();
        if (t.isPresent()) {
            return t.get();
        }
        throw Exceptions.handle()
                        .to(BizController.LOG)
                        .withSystemErrorMessage("A tenant of type Tenant was expected but not present!")
                        .handle();
    }

    public boolean hasTenant() {
        return getCurrentTenant().isPresent();
    }
}
