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
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.UserContext;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Provides smart values for a {@link UserAccount}.
 */
@Register
public class UserAccountSmartValueProvider implements SmartValueProvider {

    @Part
    @Nullable
    private Tenants<?, ?, ?> tenants;

    @Override
    public void collectValues(String type, Object payload, Consumer<SmartValue> valueCollector) {
        if (payload instanceof UserAccount<?, ?> user) {
            if (tenants != null
                && UserContext.getCurrentUser().hasPermission("permission-select-tenant")
                && !Strings.areEqual(user.getTenantAsString(), UserContext.getCurrentUser().getTenantId())) {
                valueCollector.accept(new SmartValue("fa-solid fa-exchange-alt",
                                                     tenants.fetchCachedTenantName(user.getTenantAsString()),
                                                     "/tenants/select/" + user.getTenantAsString(),
                                                     null));
            }
            if (UserContext.getCurrentUser().hasPermission("permission-select-user-account")
                && !Strings.areEqual(user.getUniqueName(), UserContext.getCurrentUser().getUserId())) {
                valueCollector.accept(new SmartValue("fa-solid fa-exchange-alt",
                                                     user.getUserAccountData().getShortName(),
                                                     "/user-accounts/select/" + user.getIdAsString(),
                                                     null));
            }
        }
    }

    @Override
    public void deriveSmartValues(String type, Object payload, BiConsumer<String, Object> derivedSmartValueCollector) {
        if ((payload instanceof UserAccount<?, ?> user) && (Strings.isFilled(user.getUserAccountData().getEmail()))) {
            derivedSmartValueCollector.accept(EmailSmartValueProvider.VALUE_TYPE_EMAIL,
                                              user.getUserAccountData().getEmail());
        }
    }

    @Override
    public int getPriority() {
        return 100;
    }
}
