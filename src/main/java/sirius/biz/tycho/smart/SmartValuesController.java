/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.smart;

import sirius.biz.web.BizController;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.PriorityParts;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.services.InternalService;
import sirius.web.services.JSONStructuredOutput;

import java.util.List;
import java.util.Optional;

/**
 * Provides the JSON / AJAX API used by <tt>t:smartValue</tt> to load additional data.
 */
@Register
public class SmartValuesController extends BizController {

    @PriorityParts(SmartValueResolver.class)
    private List<SmartValueResolver<?>> resolvers;

    @PriorityParts(SmartValueProvider.class)
    private List<SmartValueProvider> providers;

    /**
     * Supplies smart values as JSON data for the given REST call.
     *
     * @param webContext the request to respond to
     * @param output     the JSON to generate
     */
    @Routed("/tycho/smartValues")
    @InternalService
    @LoginRequired
    public void smartValues(WebContext webContext, JSONStructuredOutput output) {
        String type = webContext.require("type").asString();
        String payloadData = webContext.require("payload").getString();

        if (!verifyURISignature(webContext, type + "/" + payloadData, webContext.require("securityHash").asString())) {
            return;
        }

        Object payload = resolvePayload(type, payloadData);
        if (payload != null) {
            output.beginArray("values");
            collectValues(output, type, payload);
            output.endArray();
        }
    }

    private void collectValues(JSONStructuredOutput output, String type, Object payload) {
        for (SmartValueProvider provider : providers) {
            provider.deriveSmartValues(type,
                                       payload,
                                       (innerType, innerPayload) -> collectValues(output, innerType, innerPayload));
            provider.collectValues(type, payload, value -> output.object("value", value));
        }
    }

    private Object resolvePayload(String type, String payload) {
        if (Strings.isEmpty(payload)) {
            return null;
        }

        for (SmartValueResolver<?> resolver : resolvers) {
            Optional<?> resolvedValue = resolver.tryResolve(type, payload);
            if (resolvedValue.isPresent()) {
                return resolvedValue.get();
            }
        }

        return payload;
    }
}
