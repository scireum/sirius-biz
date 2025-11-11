/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.web.jwt;

import com.nimbusds.jwt.JWTClaimNames;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helps to build and sign a JWT.
 * <p>
 * Create new instances via {@link Jwts#builder()}.
 */
public abstract class JwtBuilder {

    protected Map<String, Object> claims = new HashMap<>();

    /**
     * Adds a claim to the JWT.
     *
     * @param name  the name of the claim
     * @param value the value to store
     * @return the builder itself for fluent method calls
     */
    public JwtBuilder withClaim(String name, Object value) {
        this.claims.put(name, value);
        return this;
    }

    /**
     * Adds a value to an array claim.
     * <p>
     * If no claim with the given name exists, a new list is created. If the current value isn't a list, it is
     * overwritten by a list which <b>only</b> contains the given <tt>value</tt>.
     *
     * @param claim the name of the array to append the value to
     * @param value the value to append
     * @return the builder itself for fluent method calls
     */
    @SuppressWarnings("unchecked")
    public JwtBuilder addClaim(String claim, Object value) {
        if (this.claims.get(claim) instanceof List<?> values) {
            ((List<Object>) values).add(value);
        } else {
            List<Object> values = new ArrayList<>();
            values.add(value);
            this.claims.put(claim, values);
        }

        return this;
    }

    /**
     * Provides a subject (<tt>sub</tt>) claim.
     *
     * @param subject the subject to provide
     * @return the builder itself for fluent method calls
     */
    public JwtBuilder withSubject(String subject) {
        this.claims.put(JWTClaimNames.SUBJECT, subject);
        return this;
    }

    /**
     * Provides an audience (<tt>aud</tt>) claim.
     *
     * @param audience the audience to provide
     * @return the builder itself for fluent method calls
     */
    public JwtBuilder withAudience(String audience) {
        this.claims.put(JWTClaimNames.AUDIENCE, audience);
        return this;
    }

    /**
     * Builds and signs the JWT.
     * <p>
     * The generated string can be directly sent (e.g. as bearer token in an authentication header).
     *
     * @return the JWT encoded as string
     */
    public abstract String build();
}
