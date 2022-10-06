/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import com.alibaba.fastjson.JSONObject;
import sirius.biz.tenants.Tenants;
import sirius.biz.tenants.UserAccount;
import sirius.db.mixing.Mixing;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;

/**
 * Permits to select a {@link UserAccount} as parameter.
 */
public class UserAccountParameter extends ParameterBuilder<UserAccount<?, ?>, UserAccountParameter> {

    @Part
    @Nullable
    private static Tenants<?, ?, ?> tenants;

    @Part
    private static Mixing mixing;

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    public UserAccountParameter(String name, String label) {
        super(name, label);
    }

    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/params/user-account.html.pasta";
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        return resolveFromString(input).map(UserAccount::getIdAsString).orElse(null);
    }

    @Override
    public Optional<?> computeValueUpdate(Map<String, String> parameterContext) {
        return updater.apply(parameterContext)
                      .map(value -> new JSONObject().fluentPut("value", value.getIdAsString())
                                                    .fluentPut("text", value.toString()));
    }

    @Override
    protected Optional<UserAccount<?, ?>> resolveFromString(Value input) {
        UserAccount<?, ?> user = mixing.getDescriptor(tenants.getUserClass())
                                       .getMapper()
                                       .find(tenants.getUserClass(), input.asString())
                                       .orElse(null);
        if (user == null || !user.getTenant().is(tenants.getRequiredTenant())) {
            return Optional.empty();
        }
        return Optional.of(user);
    }
}
