/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.isenguard;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.http.Firewall;
import sirius.web.http.WebContext;

/**
 * Provides a {@link Firewall} and rate-limiting facility.
 * <p>
 * Most probably this will be based on {@link RedisLimiter redis} but other approaches can be taken by providing a
 * custom {@link Limiter} via the system config value <tt>isenguard.limiter</tt>.
 */
@Register(framework = Isenguard.FRAMEWORK_ISENGUARD)
public class IsenguardFirewall implements Firewall {

    @Part
    private Isenguard isenguard;

    @Override
    public boolean isIPBlacklisted(WebContext webContext) {
        return isenguard.isIPBlacklisted(webContext.getRemoteIP().getHostAddress());
    }

    @Override
    public boolean handleRateLimiting(WebContext webContext, String realm) {
        String ip = webContext.getRemoteIP().getHostAddress();
        boolean rateLimitReached = isenguard.isRateLimitReached(ip,
                                                                realm,
                                                                Isenguard.USE_LIMIT_FROM_CONFIG,
                                                                () -> RateLimitingInfo.fromWebContext(webContext,
                                                                                                      null));
        if (rateLimitReached) {
            webContext.respondWith().error(HttpResponseStatus.TOO_MANY_REQUESTS);
            return true;
        }

        return false;
    }
}
