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
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.AsymmetricJWK;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import sirius.kernel.Sirius;
import sirius.kernel.SiriusExtension;
import sirius.kernel.di.std.Part;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link Jwts#verifySignature(SignedJWT)} accepts both signing setups and rejects everything else.
 * <p>
 * The key pairs are generated per run and written to the PEM files named by the test configuration. This works
 * because {@link Jwts} parses those files lazily, on the first verification - no key material is therefore
 * committed to the repository. The RSA key is the primary one, so tokens created via {@link Jwts#builder()} are
 * signed with it, and the shared secret path is signed by the test itself.
 */
@ExtendWith(SiriusExtension.class)
class JwtsTest {

    private static final Path KEY_DIRECTORY = Path.of("target", "jwt-test-keys");

    private static final byte[] LINE_SEPARATOR = "\n".getBytes(StandardCharsets.US_ASCII);

    private static ECKey secondaryKey;

    @Part
    private static Jwts jwts;

    @BeforeAll
    static void generateConfiguredKeys() throws Exception {
        secondaryKey = new ECKeyGenerator(Curve.P_256).generate();
        Files.createDirectories(KEY_DIRECTORY);
        writePemFile(KEY_DIRECTORY.resolve("rsa.pem"), new RSAKeyGenerator(2048).generate());
        writePemFile(KEY_DIRECTORY.resolve("ec.pem"), secondaryKey);
    }

    /**
     * Writes the given key pair as PEM, containing the private as well as the public key.
     * <p>
     * The public key is essential for elliptic curve keys, as its point cannot be derived from a PKCS#8 encoded
     * private key - {@link JWK#parseFromPEMEncodedObjects(String)} would reject such a file.
     */
    private static void writePemFile(Path file, AsymmetricJWK key) throws IOException, JOSEException {
        PrivateKey privateKey = key.toPrivateKey();
        PublicKey publicKey = key.toPublicKey();

        Files.writeString(file, encodeBlock("PRIVATE KEY", privateKey.getEncoded())
                                + encodeBlock("PUBLIC KEY", publicKey.getEncoded()));
    }

    private static String encodeBlock(String label, byte[] data) {
        return "-----BEGIN %s-----%n%s%n-----END %s-----%n".formatted(label,
                                                                      Base64.getMimeEncoder(64, LINE_SEPARATOR)
                                                                            .encodeToString(data),
                                                                      label);
    }

    @Test
    void acceptsATokenSignedWithTheConfiguredKey() throws Exception {
        SignedJWT jwt = SignedJWT.parse(jwts.builder().withSubject("42").build());

        assertTrue(JWSAlgorithm.Family.RSA.contains(jwt.getHeader().getAlgorithm()),
                   "As a PEM file is configured, the token must be signed with the key");
        assertTrue(jwts.verifySignature(jwt));
    }

    @Test
    void acceptsATokenSignedWithASecondaryKeyOfAnotherType() throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).type(JOSEObjectType.JWT).build(),
                                      claims());
        jwt.sign(new ECDSASigner(secondaryKey));

        assertTrue(jwts.verifySignature(jwt),
                   "The RSA verifier cannot handle ES256 and rejects the token - the EC key must still be tried");
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
        assertTrue(jwts.verifySignature(signWithSecret(Sirius.getSettings()
                                                             .getString("security.jwt.sharedSecret"))));
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
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
                                      claims());
        jwt.sign(new MACSigner(secret));

        return jwt;
    }

    private JWTClaimsSet claims() {
        return new JWTClaimsSet.Builder().subject("42").build();
    }
}
