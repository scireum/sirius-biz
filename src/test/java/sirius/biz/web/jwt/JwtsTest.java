/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.web.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import sirius.kernel.Sirius;
import sirius.kernel.SiriusExtension;
import sirius.kernel.di.std.Part;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link Jwts#verifySignature(SignedJWT)} accepts both signing setups and rejects everything else.
 * <p>
 * The test configuration fills <tt>security.jwt.jwksPemFiles</tt> as well as <tt>security.jwt.sharedSecret</tt>, so
 * tokens created via {@link Jwts#builder()} are signed with the key. Tokens for the shared secret path are
 * therefore signed by the test itself, using the secret from the system configuration.
 */
@ExtendWith(SiriusExtension.class)
class JwtsTest {

    @Part
    private static Jwts jwts;

    @Test
    void acceptsATokenSignedWithTheConfiguredKey() throws Exception {
        SignedJWT jwt = SignedJWT.parse(jwts.builder().withSubject("42").build());

        assertTrue(JWSAlgorithm.Family.RSA.contains(jwt.getHeader().getAlgorithm()),
                   "As a PEM file is configured, the token must be signed with the key");
        assertTrue(jwts.verifySignature(jwt));
    }

    @Test
    void rejectsATokenSignedWithAnotherKey() throws Exception {
        assertFalse(jwts.verifySignature(signWithKey(new RSAKeyGenerator(2048).generate(), null)));
    }

    @Test
    void rejectsATokenNamingAnUnknownKey() throws Exception {
        RSAKey foreignKey = new RSAKeyGenerator(2048).keyID("some-other-key").generate();

        assertFalse(jwts.verifySignature(signWithKey(foreignKey, foreignKey.getKeyID())),
                    "A token naming an unknown key must not be verified against the configured keys");
    }

    @Test
    void acceptsATokenSignedWithTheConfiguredSharedSecret() throws Exception {
        assertTrue(jwts.verifySignature(signWithSecret(Sirius.getSettings().getString("security.jwt.sharedSecret"))));
    }

    @Test
    void rejectsATokenSignedWithAnotherSharedSecret() throws Exception {
        assertFalse(jwts.verifySignature(signWithSecret("SOME-OTHER-SECRET-WHICH-IS-LONG-ENOUGH")));
    }

    private SignedJWT signWithKey(RSAKey key, String keyId) throws JOSEException {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(keyId).build();
        SignedJWT jwt = new SignedJWT(header, claims());
        jwt.sign(new RSASSASigner(key));

        return jwt;
    }

    private SignedJWT signWithSecret(String secret) throws JOSEException {
        SignedJWT jwt =
                new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(), claims());
        jwt.sign(new MACSigner(secret));

        return jwt;
    }

    private JWTClaimsSet claims() {
        return new JWTClaimsSet.Builder().subject("42").build();
    }
}
