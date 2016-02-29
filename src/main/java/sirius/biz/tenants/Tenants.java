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
@Register(classes = {Tenants.class, Initializable.class})
public class Tenants implements Initializable {

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

    @Override
    public void initialize() throws Exception {
        try {
            if (!oma.select(Tenant.class).exists()) {
                BizController.LOG.INFO("No tenant is present, creating system tenant....");
                Tenant tenant = new Tenant();
                tenant.setName("System Tenant");
                oma.update(tenant);
            }
            if (!oma.select(UserAccount.class).exists()) {
                BizController.LOG.INFO(
                        "No user account is present, creating system / system - Please change the password now!");
                UserAccount ua = new UserAccount();
                ua.getTenant().setValue(oma.select(Tenant.class).orderAsc(Tenant.ID).queryFirst());
                ua.getLogin().setUsername("system");
                ua.getLogin().setCleartextPassword("system");
                oma.update(ua);
            }
        } catch (Throwable e) {
            Exceptions.handle()
                      .to(BizController.LOG)
                      .error(e)
                      .withSystemErrorMessage("Cannot initialize tenants or user accounts: %s (%s)")
                      .handle();
        }
    }
}
