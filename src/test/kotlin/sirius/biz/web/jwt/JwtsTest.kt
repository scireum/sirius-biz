package sirius.biz.web.jwt

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.AsymmetricJWK
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.extension.ExtendWith
import sirius.kernel.Sirius
import sirius.kernel.SiriusExtension
import sirius.kernel.di.std.Part
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies that [Jwts.verifySignature] accepts both signing setups and rejects everything else.
 *
 * The key pairs are generated per run and written to the PEM files named by the test configuration. This works
 * because [Jwts] parses those files lazily, on the first verification - no key material is therefore committed to
 * the repository. The RSA key is the primary one, so tokens created via [Jwts.builder] are signed with it, and the
 * shared secret path is signed by the test itself.
 */
@ExtendWith(SiriusExtension::class)
class JwtsTest {

    @Test
    fun `accepts a token signed with the configured key`() {
        val jwt = SignedJWT.parse(jwts.builder().withSubject("42").build())

        assertTrue(
            JWSAlgorithm.Family.RSA.contains(jwt.header.algorithm),
            "As a PEM file is configured, the token must be signed with the key"
        )
        assertTrue(jwts.verifySignature(jwt))
    }

    @Test
    fun `accepts a token signed with a secondary key of another type`() {
        val jwt = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.ES256).type(JOSEObjectType.JWT).build(),
            claims()
        )
        jwt.sign(ECDSASigner(secondaryKey))

        assertTrue(
            jwts.verifySignature(jwt),
            "The RSA verifier cannot handle ES256 and rejects the token - the EC key must still be tried"
        )
    }

    @Test
    fun `rejects a token signed with another key`() {
        assertFalse(jwts.verifySignature(signWithKey(RSAKeyGenerator(2048).generate(), null)))
    }

    @Test
    fun `rejects a token naming an unknown key`() {
        val foreignKey = RSAKeyGenerator(2048).keyID("some-other-key").generate()

        assertFalse(
            jwts.verifySignature(signWithKey(foreignKey, foreignKey.keyID)),
            "A token naming an unknown key must not be verified against the configured keys"
        )
    }

    @Test
    fun `accepts a token signed with the configured shared secret`() {
        assertTrue(jwts.verifySignature(signWithSecret(Sirius.getSettings().getString("security.jwt.sharedSecret"))))
    }

    @Test
    fun `rejects a token signed with another shared secret`() {
        assertFalse(jwts.verifySignature(signWithSecret("SOME-OTHER-SECRET-WHICH-IS-LONG-ENOUGH")))
    }

    private fun signWithKey(key: RSAKey, keyId: String?): SignedJWT {
        val header = JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(keyId).build()
        val jwt = SignedJWT(header, claims())
        jwt.sign(RSASSASigner(key))

        return jwt
    }

    private fun signWithSecret(secret: String): SignedJWT {
        val jwt = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
            claims()
        )
        jwt.sign(MACSigner(secret))

        return jwt
    }

    private fun claims(): JWTClaimsSet = JWTClaimsSet.Builder().subject("42").build()

    companion object {

        private val KEY_DIRECTORY: Path = Path.of("target", "jwt-test-keys")

        private val LINE_SEPARATOR = "\n".toByteArray(StandardCharsets.US_ASCII)

        private lateinit var secondaryKey: ECKey

        @Part
        @JvmStatic
        private lateinit var jwts: Jwts

        @BeforeAll
        @JvmStatic
        fun generateConfiguredKeys() {
            secondaryKey = ECKeyGenerator(Curve.P_256).generate()
            Files.createDirectories(KEY_DIRECTORY)
            writePemFile(KEY_DIRECTORY.resolve("rsa.pem"), RSAKeyGenerator(2048).generate())
            writePemFile(KEY_DIRECTORY.resolve("ec.pem"), secondaryKey)
        }

        /**
         * Writes the given key pair as PEM, containing the private as well as the public key.
         *
         * The public key is essential for elliptic curve keys, as its point cannot be derived from a PKCS#8 encoded
         * private key - [JWK.parseFromPEMEncodedObjects] would reject such a file.
         */
        private fun writePemFile(file: Path, key: AsymmetricJWK) {
            Files.writeString(
                file,
                encodeBlock("PRIVATE KEY", key.toPrivateKey().encoded)
                        + encodeBlock("PUBLIC KEY", key.toPublicKey().encoded)
            )
        }

        private fun encodeBlock(label: String, data: ByteArray): String {
            return "-----BEGIN %s-----\n%s\n-----END %s-----\n".format(
                label,
                Base64.getMimeEncoder(64, LINE_SEPARATOR).encodeToString(data),
                label
            )
        }
    }
}
