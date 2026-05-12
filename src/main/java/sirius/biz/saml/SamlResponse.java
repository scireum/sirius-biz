/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.saml;

import sirius.kernel.commons.MultiMap;
import sirius.kernel.commons.Strings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Locale;

/**
 * Represents the parsed payload of a SAML response.
 */
public class SamlResponse {

    private final String issuer;
    private final String fingerprint;
    private final String nameId;
    private final MultiMap<String, String> attributes;

    /**
     * Contains the attribute URI used to transmit a security group.
     */
    public static final String ATTRIBUTE_GROUP = "http://schemas.xmlsoap.org/claims/Group";

    /**
     * Contains the attribute URI used to transmit a role of the user.
     * <p>
     * This can be used to submit a readable security group in Azure AD as the {@link #ATTRIBUTE_GROUP groups} are
     * always UUIDs.
     */
    public static final String ATTRIBUTE_ROLE = "http://schemas.microsoft.com/ws/2008/06/identity/claims/role";

    /**
     * Contains the attribute URI used to transmit the given name of the user being authenticated.
     */
    public static final String ATTRIBUTE_GIVEN_NAME = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname";

    /**
     * Contains the attribute URI used to transmit the surname of the user being authenticated.
     */
    public static final String ATTRIBUTE_SURNAME = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname";

    /**
     * Contains the attribute URI used to transmit the email address of the user being authenticated.
     */
    public static final String ATTRIBUTE_EMAIL_ADDRESS =
            "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress";

    /**
     * Creates a new SAML response.
     *
     * @param issuer      the issuer which created the SAML response
     * @param fingerprint the fingerprint of the certificate which was used to sign the response
     * @param nameId      the name of the user
     * @param attributes  the attributes within the <tt>Assertion</tt>
     */
    public SamlResponse(String issuer, String fingerprint, String nameId, MultiMap<String, String> attributes) {
        this.issuer = issuer;
        this.fingerprint = fingerprint;
        this.nameId = nameId;
        this.attributes = attributes;
    }

    /**
     * Returns the issuer of the assertion.
     *
     * @return the issuer of the assertion
     */
    public String getIssuer() {
        return issuer;
    }

    /**
     * Returns the fingerprint of the certificate which was used to sign the response.
     * <p>
     * This fingerprint has to be compared against an externally configured trust source. The certificate embedded in
     * the SAML response must not be treated as trusted by itself.
     *
     * @return the fingerprint of the X509 certificate
     */
    public String getFingerprint() {
        return fingerprint;
    }

    /**
     * Determines if the response was signed with the expected certificate fingerprint.
     * <p>
     * This accepts common fingerprint formatting differences such as upper-case hex, colon separators or whitespace.
     *
     * @param expectedFingerprint the externally configured trusted fingerprint
     * @return <tt>true</tt> if the fingerprint matches, <tt>false</tt> otherwise
     */
    public boolean hasFingerprint(@Nullable String expectedFingerprint) {
        return Strings.areEqual(fingerprint, normalizeFingerprint(expectedFingerprint));
    }

    /**
     * Normalizes a certificate fingerprint for comparisons.
     *
     * @param fingerprint the fingerprint to normalize
     * @return the normalized fingerprint
     */
    @Nonnull
    public static String normalizeFingerprint(@Nullable String fingerprint) {
        if (Strings.isEmpty(fingerprint)) {
            return "";
        }

        return fingerprint.replace(":", "").replaceAll("\\s", "").toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the username or id which has been authenticated.
     *
     * @return the username
     */
    public String getNameId() {
        return nameId;
    }

    /**
     * Returns all attributes which were submitted for the given attribute URI.
     *
     * @param name the name or attribute URI
     * @return a collection containing all values submitted for the given attribute
     * or an empty collection if there are none.
     */
    @Nonnull
    public Collection<String> getAttribute(String name) {
        return attributes.get(name);
    }

    /**
     * Returns the first attribute value submitted for the given attribute URI.
     *
     * @param name the name or attribute URI
     * @return the first value being submitted or <tt>null</tt> if there was no value
     */
    @Nullable
    public String getAttributeValue(String name) {
        return attributes.get(name).stream().findFirst().orElse(null);
    }
}
