/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.smart;

import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.Tenants;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Provides smart values for a {@link Tenant}.
 */
@Register
public class TenantSmartValueProvider implements SmartValueProvider {

    @Part
    @Nullable
    private Tenants<?, ?, ?> tenants;

    @Override
    public void collectValues(String type, Object payload, Consumer<SmartValue> valueCollector) {
        if (tenants == null) {
            return;
        }
        if (!(payload instanceof Tenant<?> tenant)) {
            return;
        }

        UserInfo currentUser = UserContext.getCurrentUser();
        if (!currentUser.hasPermission("permission-select-tenant")) {
            return;
        }
        if (Strings.areEqual(tenant.getIdAsString(), currentUser.getTenantId())) {
            return;
        }

        valueCollector.accept(new SmartValue("fa-solid fa-exchange-alt",
                                             NLS.get("TenantController.select"),
                                             "/tenants/select/" + tenant.getIdAsString(),
                                             null));

        valueCollector.accept(new SmartValue("fa-solid fa-pen-to-square",
                                             NLS.get("TenantController.edit"),
                                             "/tenant/" + tenant.getIdAsString(),
                                             null));
    }

    @Override
    public void deriveSmartValues(String type, Object payload, BiConsumer<String, Object> derivedSmartValueCollector) {
        // No values to be derived...
    }

    @Override
    public int getPriority() {
        return 100;
    }
}
