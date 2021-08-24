/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.smart;

import sirius.biz.tenants.Tenants;
import sirius.biz.tenants.UserAccount;
import sirius.kernel.commons.Explain;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.di.std.Register;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Resolves {@link sirius.biz.tenants.UserAccount user accounts} utilizing the cache of the
 * {@link sirius.biz.tenants.TenantUserManager}.
 */
@Register
public class UserAccountResolver implements SmartValueResolver<UserAccount<?,?>> {

    public static final String TYPE_USER_ACCOUNT = "user-account";

    @Part
    @Nullable
    private Tenants<?, ?, ?> tenants;

    @SuppressWarnings({"unchecked", "SimplifyOptionalCallChains"})
    @Explain("flapMap raises a generics cast error in this case.")
    @Override
    public Optional<UserAccount<?, ?>> tryResolve(String type, String payload) {
        if (tenants != null && TYPE_USER_ACCOUNT.equals(type)) {
            return Optional.ofNullable(tenants.getTenantUserManager().findUserByUserId(payload))
                           .map(user -> user.tryAs(UserAccount.class).orElse(null));
        }

        return Optional.empty();
    }

    @Override
    public int getPriority() {
        return Priorized.DEFAULT_PRIORITY;
    }
}
