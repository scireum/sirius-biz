/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.web.BizController;
import sirius.kernel.di.Initializable;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.mixing.OMA;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import java.util.Optional;

/**
 * Created by aha on 11.05.15.
 */
@Framework("tenants")
@Register(classes = Tenants.class)
public class Tenants  {

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
        return getCurrentUser().flatMap(u -> Optional.ofNullable(u.getTenant().getValue()));
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

    @Part
    private OMA oma;

}
