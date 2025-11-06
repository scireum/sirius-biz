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

@Register
public class JwksController extends BasicController {

    @Part
    private Jwts jwks;

    @Routed("/.well-known/jwks.json")
    public void jwks(WebContext context) {
        JSONStructuredOutput json = context.respondWith().json();
        json.beginResult();
        json.beginArray("jwk");
        for (JWK key : jwks.getPublicKeys()) {
            json.beginObject("key");
            key.toJSONObject().forEach(json::property);
            json.endObject();
        }
        json.endArray();
        json.endResult();

        System.out.println(jwks.builder().withSubject("test").build()    );
    }
}
