/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.web.jwt;

import com.nimbusds.jose.jwk.JWK;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.controller.BasicController;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.services.JSONStructuredOutput;

/**
 * Provides the available public keys which are used for signing JWTs as JWKS (JSON Web Key Set).
 * <p>
 * These keys or better key pairs are loaded by {@link Jwts}. This controller provides a simple route which
 * can be used by downstream services to identify which keys are or were used to sign JWTs and thus verify their
 * signatures.
 */
@Register
public class JwksController extends BasicController {

    @Part
    private Jwts jwts;

    /**
     * Provides a commonly used URI to discover which public keys may be used to verify signatures of generated JWTs.
     *
     * @param context the request to respond to
     */
    @Routed("/.well-known/jwks.json")
    public void jwks(WebContext context) {
        JSONStructuredOutput json = context.respondWith().json();
        json.beginResult();
        {
            json.beginArray("jwk");
            for (JWK key : jwts.getPublicKeys()) {
                json.beginObject("key");
                key.toJSONObject().forEach(json::property);
                json.endObject();
            }
            json.endArray();
        }
        json.endResult();
    }
}
