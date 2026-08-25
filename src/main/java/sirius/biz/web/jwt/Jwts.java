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
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.Requirement;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimNames;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import sirius.kernel.commons.Hasher;
import sirius.kernel.commons.Streams;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.AutoRegister;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import javax.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Helps to create, sign, and verify JWTs (JSON Web Tokens).
 * <p>
 * These can be used to authenticate this server or a user against other services. There are two methods to do so.
 * Generated JWTs can be signed with a symmetrical hash function using a shared secret which is fetched from the
 * system configuration (<tt>security.jwt.sharedSecret</tt>). The second approach is to generate a public / private
 * key pair and store it in a PEM file. The file (or actually files - to support key rotation) is passed in via
 * <tt>security.jwt.jwksPemFiles</tt>. The private key (of the first file in the list) is then used to sign the JWTs
 * where as all public keys are exposed using the {@link JwksController} under the uri <tt>/.well-known/jwks.json</tt>.
 * This URI is then scraped by downstream services when validating the given keys. This permits a simple setup while
 * still supporting key rotation. Therefore, this is the preferred way to set up a production scenario whereas the
 * shared secret approach should only be used to testing and development purposes.
 */
@Register
@AutoRegister
public class Jwts {

    @ConfigValue("security.jwt.jwksPemFiles")
    private String jwksPemFiles;

    @ConfigValue("security.jwt.issuer")
    private String issuer;

    @ConfigValue("security.jwt.expiry")
    private Duration expiry;

    @ConfigValue("security.jwt.nbfThreshold")
    private Duration nbfThreshold;

    @ConfigValue("security.jwt.sharedSecret")
    private String sharedSecret;

    private JWK primaryKey;
    private List<JWK> keys;

    private JWK parsePemFile(String path) {
        try {
            String data = Streams.readToString(new FileReader(path, StandardCharsets.UTF_8));
            String keyId = Hasher.sha256().hashBytes(data.getBytes(StandardCharsets.UTF_8)).toHexString();
            JWK rawKey = JWK.parseFromPEMEncodedObjects(data);
            return switch (rawKey) {
                case RSAKey rsaKey -> new RSAKey.Builder(rsaKey).keyID(keyId).build();
                case ECKey ecKey -> new ECKey.Builder(ecKey).keyID(keyId).build();
                default -> {
                    Log.SYSTEM.SEVERE("Failed loading JWK from PEM file: %s - Unsupported key type: %s (%s)",
                                      path,
                                      rawKey,
                                      rawKey.getClass().getName());
                    yield null;
                }
            };
        } catch (FileNotFoundException e) {
            Log.SYSTEM.WARN("Skipping non-existent PEM file as JWKS signing key: %s", path);
            Exceptions.ignore(e);

            return null;
        } catch (IOException | JOSEException e) {
            Exceptions.handle()
                      .to(Log.SYSTEM)
                      .error(e)
                      .withSystemErrorMessage("Failed to load PEM file '%s' as JWKS signing key: %s (%s)", path)
                      .handle();

            return null;
        }
    }

    private void parse() {
        if (Strings.isFilled(jwksPemFiles)) {
            List<JWK> parsedKeys = Stream.of(jwksPemFiles.split(";"))
                                         .filter(Strings::isFilled)
                                         .map(this::parsePemFile)
                                         .filter(Objects::nonNull)
                                         .toList();
            keys = parsedKeys;
            primaryKey = parsedKeys.stream().findFirst().orElse(null);
        } else {
            keys = Collections.emptyList();
        }
    }

    @Nullable
    private JWK getPrimaryKey() {
        if (keys == null) {
            parse();
        }

        return primaryKey;
    }

    protected List<JWK> getPublicKeys() {
        if (keys == null) {
            parse();
        }

        return keys.stream().map(JWK::toPublicJWK).filter(Objects::nonNull).toList();
    }

    protected Optional<JWSAlgorithm> determineAlgorithm(JWK signingKey) {
        return switch (signingKey) {
            case RSAKey _ -> RSASSASigner.SUPPORTED_ALGORITHMS.stream().filter(isRecommendedAlgorithm()).findFirst();
            case ECKey _ -> ECDSASigner.SUPPORTED_ALGORITHMS.stream().filter(isRecommendedAlgorithm()).findFirst();
            default -> Optional.empty();
        };
    }

    /**
     * Generated s builder which can be used to build and sign a JWT using the current system configuration.
     *
     * @return a builder used to create and sign a JWT
     * @see sirius.kernel.commons.Outcall#withBearerToken(String)
     */
    public JwtBuilder builder() {
        return new JwtBuilder() {
            @Override
            public String build() {
                return Jwts.this.sign(this.claims);
            }
        };
    }

    /**
     * Verifies the signature of the given JWT using the keys of the current system configuration.
     * <p>
     * This is the counterpart of {@link #builder()} and accepts the same two setups: tokens signed with a
     * symmetrical hash function are verified against <tt>security.jwt.sharedSecret</tt>, all others against the
     * public keys of <tt>security.jwt.jwksPemFiles</tt>. If the token names a key via its <tt>kid</tt> header, only
     * that key is considered, which keeps a key rotation unambiguous.
     * <p>
     * Note that only the signature is checked. Whether the claims of the token are acceptable - especially its
     * expiry - has to be decided by the caller.
     *
     * @param jwt the token to verify
     * @return <tt>true</tt> if the signature is valid, <tt>false</tt> otherwise
     */
    public boolean verifySignature(SignedJWT jwt) {
        try {
            if (JWSAlgorithm.Family.HMAC_SHA.contains(jwt.getHeader().getAlgorithm())) {
                return Strings.isFilled(sharedSecret) && jwt.verify(new MACVerifier(sharedSecret));
            }

            String keyId = jwt.getHeader().getKeyID();
            for (JWK key : getPublicKeys()) {
                if (Strings.isFilled(keyId) && !Strings.areEqual(keyId, key.getKeyID())) {
                    continue;
                }

                JWSVerifier verifier = determineVerifier(key);
                if (verify(jwt, verifier)) {
                    return true;
                }
            }

            return false;
        } catch (JOSEException e) {
            Exceptions.handle()
                      .to(Log.SYSTEM)
                      .error(e)
                      .withSystemErrorMessage("Failed to verify a JWT signature: %s (%s)")
                      .handle();

            return false;
        }
    }

    private String sign(Map<String, Object> providedClaimsSet) {
        JWTClaimsSet.Builder claimsSetBuilder = new JWTClaimsSet.Builder();
        providedClaimsSet.forEach(claimsSetBuilder::claim);
        if (Strings.isFilled(issuer)) {
            claimsSetBuilder.issuer(issuer);
        }

        // Note that we provide the timing data manually as the library only supports legacy Date APIs...
        claimsSetBuilder.claim(JWTClaimNames.NOT_BEFORE,
                               Instant.now().minusSeconds(nbfThreshold.getSeconds()).getEpochSecond());
        claimsSetBuilder.claim(JWTClaimNames.EXPIRATION_TIME,
                               Instant.now().plusSeconds(expiry.getSeconds()).getEpochSecond());
        claimsSetBuilder.claim(JWTClaimNames.ISSUED_AT, Instant.now().getEpochSecond());

        if (getPrimaryKey() != null) {
            return signUsingKey(claimsSetBuilder.build());
        } else {
            return signUsingSharedSecret(claimsSetBuilder.build());
        }
    }

    private String signUsingKey(JWTClaimsSet claimsSet) {
        JWK signingKey = getPrimaryKey();
        JWSSigner signer = determineSigner(signingKey);
        JWSAlgorithm algorithm = signer.supportedJWSAlgorithms()
                                       .stream()
                                       .filter(isRecommendedAlgorithm())
                                       .findFirst()
                                       .orElseThrow(() -> Exceptions.handle()
                                                                    .to(Log.SYSTEM)
                                                                    .withSystemErrorMessage(
                                                                            "Failed to sign JWT using key %s (%s): No recommended algorithm found.",
                                                                            signingKey,
                                                                            signingKey.getClass().getName())
                                                                    .handle());
        try {
            JWSHeader header =
                    new JWSHeader.Builder(algorithm).type(JOSEObjectType.JWT).keyID(signingKey.getKeyID()).build();
            SignedJWT jwt = new SignedJWT(header, claimsSet);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException e) {
            throw Exceptions.handle()
                            .to(Log.SYSTEM)
                            .error(e)
                            .withSystemErrorMessage("Failed to sign JWT using key %s (%s): %s (%s)",
                                                    signingKey,
                                                    signingKey.getClass().getName())
                            .handle();
        }
    }

    private String signUsingSharedSecret(JWTClaimsSet claimsSet) {
        if (Strings.isEmpty(sharedSecret)) {
            throw new IllegalStateException("No JWTS signing key is available. Please check the system configuration.");
        }

        try {
            JWSSigner signer = new MACSigner(sharedSecret);
            JWSAlgorithm algorithm = signer.supportedJWSAlgorithms()
                                           .stream()
                                           .filter(alg -> alg.getRequirement() == Requirement.RECOMMENDED
                                                          || alg.getRequirement() == Requirement.REQUIRED)
                                           .findFirst()
                                           .orElseThrow(() -> Exceptions.handle()
                                                                        .to(Log.SYSTEM)
                                                                        .withSystemErrorMessage(
                                                                                "Failed to sign JWT using shared secret: No recommended algorithm found.")
                                                                        .handle());

            JWSHeader header = new JWSHeader.Builder(algorithm).type(JOSEObjectType.JWT).build();
            SignedJWT jwt = new SignedJWT(header, claimsSet);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException e) {
            throw Exceptions.handle()
                            .to(Log.SYSTEM)
                            .error(e)
                            .withSystemErrorMessage("Failed to sign JWT using key shared secret: %s (%s)")
                            .handle();
        }
    }

    private Predicate<JWSAlgorithm> isRecommendedAlgorithm() {
        return alg -> alg.getRequirement() == Requirement.RECOMMENDED;
    }

    @Nullable
    private JWSVerifier determineVerifier(JWK key) throws JOSEException {
        return switch (key) {
            case RSAKey rsaKey -> new RSASSAVerifier(rsaKey);
            case ECKey ecKey -> new ECDSAVerifier(ecKey);
            default -> null;
        };
    }

    private JWSSigner determineSigner(JWK signingKey) {
        try {
            return switch (signingKey) {
                case RSAKey rsaKey -> new RSASSASigner(rsaKey);
                case ECKey ecKey -> new ECDSASigner(ecKey);
                default -> throw Exceptions.handle()
                                           .to(Log.SYSTEM)
                                           .withSystemErrorMessage("Unsupported signing key type: %s",
                                                                   signingKey.getClass())
                                           .handle();
            };
        } catch (JOSEException e) {
            throw Exceptions.handle()
                            .to(Log.SYSTEM)
                            .error(e)
                            .withSystemErrorMessage("Failed using JWK signing key %s (%s): %s (%s)",
                                                    signingKey,
                                                    signingKey.getClass().getName())
                            .handle();
        }
    }

    /**
     * Applies a single verifier to the given JWT.
     * <p>
     * A verifier that cannot handle the algorithm of the token throws instead of reporting a mismatch. As the caller
     * walks through all configured keys, such a rejection must not abort the whole verification - the next verifier may
     * well be the matching one.
     *
     * @param jwt      the token to verify
     * @param verifier the verifier to apply
     * @return <tt>true</tt> if this verifier accepts the signature, <tt>false</tt> if it rejects the signature or
     * cannot handle the token at all
     */
    private boolean verify(SignedJWT jwt, JWSVerifier verifier) {
        try {
            return verifier != null && jwt.verify(verifier);
        } catch (JOSEException _) {
            return false;
        }
    }
}
