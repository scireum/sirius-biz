/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.HandledException;
import sirius.kernel.nls.NLS;
import sirius.kernel.nls.Translation;
import sirius.web.controller.BasicController;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.services.InternalService;
import sirius.web.services.JSONStructuredOutput;

/**
 * Provides services to fetch unused or autocreated NLS keys.
 * <p>
 * This is mainly used by {@link sirius.biz.util.ReportUnusedNLSKeysJob} to identify NLS keys which are unused across
 * the cluster.
 */
@Register
public class NLSController extends BasicController {

    public static final String RESPONSE_UNUSED = "unused";
    public static final String RESPONSE_KEY = "key";
    public static final String RESPONSE_AUTOCREATED = "autocreated";

    @Part
    private InterconnectClusterManager clusterManager;

    @Override
    public void onError(WebContext webContext, HandledException error) {
        webContext.respondWith().error(HttpResponseStatus.INTERNAL_SERVER_ERROR, error);
    }

    /**
     * Reports all unused NLS keys.
     *
     * @param webContext the request to handle
     * @param out        the output to write the JSON to
     * @param token      the cluster authentication token
     */
    @Routed("/system/nls/unused/:1")
    @InternalService
    public void unused(WebContext webContext, JSONStructuredOutput out, String token) {
        if (!clusterManager.isClusterAPIToken(token)) {
            webContext.respondWith().error(HttpResponseStatus.UNAUTHORIZED);
            return;
        }

        out.beginArray(RESPONSE_UNUSED);
        NLS.getTranslationEngine()
           .getUnusedTranslations()
           .map(Translation::getKey)
           .forEach(key -> out.property(RESPONSE_KEY, key));
        out.endArray();
    }

    /**
     * Reports all autocreated NLS keys.
     *
     * @param webContext the request to handle
     * @param out        the output to write the JSON to
     * @param token      the cluster authentication token
     */
    @Routed("/system/nls/autocreated/:1")
    @InternalService
    public void autocreated(WebContext webContext, JSONStructuredOutput out, String token) {
        if (!clusterManager.isClusterAPIToken(token)) {
            webContext.respondWith().error(HttpResponseStatus.UNAUTHORIZED);
            return;
        }

        out.beginArray(RESPONSE_AUTOCREATED);
        NLS.getTranslationEngine()
           .getAutocreatedTranslations()
           .map(Translation::getKey)
           .forEach(key -> out.property(RESPONSE_KEY, key));
        out.endArray();
    }
}
