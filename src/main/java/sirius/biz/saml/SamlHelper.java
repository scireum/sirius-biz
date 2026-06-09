/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.saml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Hasher;
import sirius.kernel.commons.MultiMap;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.kernel.xml.Attribute;
import sirius.kernel.xml.StructuredNode;
import sirius.kernel.xml.XMLStructuredOutput;
import sirius.web.http.WebContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.XMLConstants;
import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorException;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Provides a helper to generate SAML 2 requests and to process responses.
 * <p>
 * The <b>Security Assertion Markup Language</b> is an XML beast to permit identity federation across cloud services.
 * This implementation provides all the tools required to become an identity consumer, which can call an identity
 * provider to authenticate a user.
 */
@Register(classes = SamlHelper.class)
public class SamlHelper {

    /**
     * Used to log all events related to SAML.
     * <p>
     * Some FINE loggings are provided which might support troubleshooting.
     */
    public static final Log LOG = Log.get("saml");

    /**
     * The clock skew accepted when validating SAML time conditions.
     */
    public static final Duration SAML_CLOCK_SKEW = Duration.ofMinutes(2);

    /**
     * The absolute upper bound for accepting a SAML assertion after its <tt>IssueInstant</tt>.
     */
    static final Duration ABSOLUTE_MAX_RESPONSE_ACCEPTANCE_DURATION = Duration.ofMinutes(30);

    /**
     * Limits the Base64 encoded SAMLResponse before decoding or XML parsing. The configured value can adapt this to
     * product-specific IdP payloads, but the effective limit is always capped at 1 MB.
     */
    static final int ABSOLUTE_MAX_ENCODED_SAML_RESPONSE_SIZE = 1_000_000;

    /**
     * Defines how long a SAML assertion is accepted after its <tt>IssueInstant</tt>.
     */
    @ConfigValue("security.saml.maxResponseAcceptanceDuration")
    private Duration maxResponseAcceptanceDuration;

    @ConfigValue("security.saml.maxEncodedResponseSize")
    private int maxEncodedSamlResponseSize;

    @Part
    private SamlReplayProtector replayProtector;

    private static final String SAML_NAMESPACE = "urn:oasis:names:tc:SAML:2.0:assertion";

    private static final String SAMLP_NAMESPACE = "urn:oasis:names:tc:SAML:2.0:protocol";

    private static final String ATTRIBUTE_ID = "ID";

    private static final String ATTRIBUTE_ISSUE_INSTANT = "IssueInstant";

    private static final String ATTRIBUTE_NOT_BEFORE = "NotBefore";

    private static final String ATTRIBUTE_NOT_ON_OR_AFTER = "NotOnOrAfter";

    private static final String FEATURE_DISALLOW_DOCTYPE_DECL = "http://apache.org/xml/features/disallow-doctype-decl";

    private static final String FEATURE_LOAD_EXTERNAL_DTD =
            "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    private static final String FEATURE_EXTERNAL_GENERAL_ENTITIES =
            "http://xml.org/sax/features/external-general-entities";

    private static final String FEATURE_EXTERNAL_PARAMETER_ENTITIES =
            "http://xml.org/sax/features/external-parameter-entities";

    private static final String XML_SIGNATURE_SECURE_VALIDATION = "org.jcp.xml.dsig.secureValidation";

    /**
     * Generates a Base64 encoded XML request which can be sent via a POST request to a SAML 2 identity provider / SAML responder.
     * This is used for the HTTP POST Binding: <a href="https://docs.oasis-open.org/security/saml/v2.0/saml-bindings-2.0-os.pdf">SAML Bindings</a> (section 3.5).
     *
     * @param issuer      the name of the issuer. This tells the identity provider "who" is asking to perform an authentication.
     * @param issuerIndex the index of the issuer. As the identity provider might manage several endpoints for a
     *                    single issuer configuration, different indices can be passed in. The default value would
     *                    be "0"
     * @return a Base64 encoded SAML2 request which can be sent via a POST request to a SAML 2 identity provider / SAML responder
     */
    public String generateAuthenticationRequestForPostBinding(String issuer, String issuerIndex) {
        return generateAuthenticationRequestForPostBinding(issuer, issuerIndex, Optional.empty());
    }

    /**
     * Generates a Base64 encoded XML request which can be sent via a POST request to a SAML 2 identity provider / SAML responder.
     * This is used for the HTTP POST Binding: <a href="https://docs.oasis-open.org/security/saml/v2.0/saml-bindings-2.0-os.pdf">SAML Bindings</a> (section 3.5).
     *
     * @param issuer      the name of the issuer. This tells the identity provider "who" is asking to perform an authentication.
     * @param issuerIndex the index of the issuer. As the identity provider might manage several endpoints for a
     *                    single issuer configuration, different indices can be passed in. The default value would
     *                    be "0"
     * @param userHint    an optional user hint to pre-fill the NameID in the request. Be aware that some identity providers might reject requests with pre-filled NameIDs.
     * @return a Base64 encoded SAML2 request which can be sent via a POST request to a SAML 2 identity provider / SAML responder
     */
    public String generateAuthenticationRequestForPostBinding(String issuer,
                                                              String issuerIndex,
                                                              Optional<SamlUserHint> userHint) {
        return Base64.getEncoder().encodeToString(createAuthenticationRequestXML(issuer, issuerIndex, userHint));
    }

    /**
     * Generates a deflated and Base64 encoded XML request which can be sent via a GET request to a SAML 2 identity provider / SAML responder
     * This is used for the HTTP Redirect Binding: <a href="https://docs.oasis-open.org/security/saml/v2.0/saml-bindings-2.0-os.pdf">SAML Bindings</a> (section 3.4).
     *
     * @param issuer      the name of the issuer. This tells the identity provider "who" is asking to perform an authentication.
     * @param issuerIndex the index of the issuer. As the identity provider might manage several endpoints for a
     *                    single issuer configuration, different indices can be passed in. The default value would
     *                    be "0"
     * @return a deflated and Base64 encoded SAML2 request which can be sent via a GET request to a SAML 2 identity provider / SAML responder
     */
    public String generateAuthenticationRequestForRedirectBinding(String issuer, String issuerIndex) {
        return generateAuthenticationRequestForRedirectBinding(issuer, issuerIndex, Optional.empty());
    }

    /**
     * Generates a deflated and Base64 encoded XML request which can be sent via a GET request to a SAML 2 identity provider / SAML responder
     * This is used for the HTTP Redirect Binding: <a href="https://docs.oasis-open.org/security/saml/v2.0/saml-bindings-2.0-os.pdf">SAML Bindings</a> (section 3.4).
     *
     * @param issuer      the name of the issuer. This tells the identity provider "who" is asking to perform an authentication.
     * @param issuerIndex the index of the issuer. As the identity provider might manage several endpoints for a
     *                    single issuer configuration, different indices can be passed in. The default value would
     *                    be "0"
     * @param userHint    an optional user hint to pre-fill the NameID in the request. Be aware that some identity providers might reject requests with pre-filled NameIDs.
     * @return a deflated and Base64 encoded SAML2 request which can be sent via a GET request to a SAML 2 identity provider / SAML responder
     */
    public String generateAuthenticationRequestForRedirectBinding(String issuer,
                                                                  String issuerIndex,
                                                                  Optional<SamlUserHint> userHint) {
        byte[] request = createAuthenticationRequestXML(issuer, issuerIndex, userHint);

        // TODO MIO-6449: Deflater is AutoClosable in Java >= 25
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION,
                                         true /* raw deflate, zlib header and checksum are not supported by SAML */);

        byte[] compressedRequest;
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(byteArrayOutputStream, deflater)) {
            deflaterOutputStream.write(request);
            deflaterOutputStream.finish();
            compressedRequest = byteArrayOutputStream.toByteArray();
        } catch (IOException exception) {
            throw Exceptions.handle(exception);
        } finally {
            deflater.end();
        }

        return Base64.getEncoder().encodeToString(compressedRequest);
    }

    /**
     * Creates the XML payload for a SAML authentication request.
     *
     * @param issuer           the issuer to place into the request
     * @param issuerIndex      the assertion consumer service index to request
     * @param optionalUserHint an optional hint for the user to authenticate
     * @return the serialized XML request
     */
    private byte[] createAuthenticationRequestXML(String issuer,
                                                  String issuerIndex,
                                                  Optional<SamlUserHint> optionalUserHint) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        XMLStructuredOutput output = new XMLStructuredOutput(buffer);
        output.beginOutput("samlp:AuthnRequest",
                           Attribute.set("xmlns:samlp", SAMLP_NAMESPACE),
                           Attribute.set("xmlns:saml", SAML_NAMESPACE),
                           Attribute.set(ATTRIBUTE_ID, "identifier_" + System.currentTimeMillis()),
                           Attribute.set("Version", "2.0"),
                           Attribute.set(ATTRIBUTE_ISSUE_INSTANT,
                                         DateTimeFormatter.ISO_INSTANT.format(Instant.now()
                                                                                     .truncatedTo(ChronoUnit.SECONDS))),
                           Attribute.set("AssertionConsumerServiceIndex", issuerIndex));
        output.property("saml:Issuer", issuer);
        output.beginObject("samlp:NameIDPolicy",
                           Attribute.set("AllowCreate", false),
                           Attribute.set("Format", "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified"));
        output.endObject();

        optionalUserHint.ifPresent(userHint -> {
            output.beginObject("saml:Subject");
            output.property("saml:NameID", userHint.value(), Attribute.set("Format", userHint.format()));
            output.endObject();
        });

        output.endOutput();

        if (LOG.isFINE()) {
            LOG.FINE("Generating SAML request: %s", buffer.toString(StandardCharsets.UTF_8));
        }

        return buffer.toByteArray();
    }

    /**
     * Parses a SAML 2 response from the given request.
     * <p>
     * Note that the fingerprint <b>must</b> be verified against an externally configured trust source, as this method
     * only checks if the signature is valid for the embedded certificate, not <b>who</b> created it.
     *
     * @param webContext the HTTP request to read the response from
     * @return the parsed response which has been verified
     */
    public SamlResponse parseSamlResponse(WebContext webContext) {
        if (!webContext.isPostRequest()) {
            throw Exceptions.createHandled().withSystemErrorMessage("Invalid SAML Response: POST expected!").handle();
        }

        String encodedResponse = webContext.get("SAMLResponse").asString();
        int maxEncodedResponseSize = determineEffectiveMaxEncodedSamlResponseSize(maxEncodedSamlResponseSize);
        if (encodedResponse.length() > maxEncodedResponseSize) {
            throw Exceptions.createHandled()
                            .withSystemErrorMessage(
                                    "Invalid SAML Response: SAMLResponse exceeds maximum size of %s bytes.",
                                    maxEncodedResponseSize)
                            .handle();
        }

        byte[] response = Base64.getDecoder().decode(encodedResponse);

        if (LOG.isFINE()) {
            LOG.FINE("Received SAML response: %s", new String(response, StandardCharsets.UTF_8));
        }

        try (InputStream input = new ByteArrayInputStream(response)) {
            return parseSamlResponse(input, true);
        } catch (HandledException exception) {
            throw exception;
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(LOG)
                            .error(exception)
                            .withSystemErrorMessage("An error occurred while parsing a SAML Response: %s (%s)")
                            .handle();
        }
    }

    /**
     * Determines the effective encoded SAML response size limit from the configured value.
     *
     * @param configuredMaxEncodedSamlResponseSize the configured maximum encoded response size in bytes
     * @return the configured limit capped by {@link #ABSOLUTE_MAX_ENCODED_SAML_RESPONSE_SIZE}
     */
    static int determineEffectiveMaxEncodedSamlResponseSize(int configuredMaxEncodedSamlResponseSize) {
        if (configuredMaxEncodedSamlResponseSize <= 0) {
            return ABSOLUTE_MAX_ENCODED_SAML_RESPONSE_SIZE;
        }

        return Math.min(configuredMaxEncodedSamlResponseSize, ABSOLUTE_MAX_ENCODED_SAML_RESPONSE_SIZE);
    }

    /**
     * Determines the effective maximum response acceptance duration from the configured value.
     *
     * @param configuredMaxResponseAcceptanceDuration the configured maximum duration after the assertion issue instant
     * @return the configured duration capped by {@link #ABSOLUTE_MAX_RESPONSE_ACCEPTANCE_DURATION}
     */
    static Duration determineEffectiveMaxResponseAcceptanceDuration(Duration configuredMaxResponseAcceptanceDuration) {
        if (configuredMaxResponseAcceptanceDuration == null || configuredMaxResponseAcceptanceDuration.isZero()
            || configuredMaxResponseAcceptanceDuration.isNegative()) {
            return ABSOLUTE_MAX_RESPONSE_ACCEPTANCE_DURATION;
        }

        return configuredMaxResponseAcceptanceDuration.compareTo(ABSOLUTE_MAX_RESPONSE_ACCEPTANCE_DURATION) > 0 ?
               ABSOLUTE_MAX_RESPONSE_ACCEPTANCE_DURATION :
               configuredMaxResponseAcceptanceDuration;
    }

    /**
     * Parses a SAML 2 response from the given input string, optionally checking timestamps.
     * <p>
     * Note that the fingerprint <b>must</b> be verified against an externally configured trust source, as this method
     * only checks if the signature is valid for the embedded certificate, not <b>who</b> created it.
     *
     * @param inputStream a stream containing the SAML XML response to parse
     * @param checkTime   a flag indicating whether to check for expired timestamps
     * @return the parsed response which has been verified
     */
    public SamlResponse parseSamlResponse(InputStream inputStream, boolean checkTime) {
        try {
            Document document = getResponseDocument(inputStream);

            Element assertion = selectSingleElement(document, SAML_NAMESPACE, "Assertion");
            TimestampValidationResult timestampValidationResult = checkTime ? verifyTimestamp(assertion) : null;
            String fingerprint = validateXMLSignature(document, assertion);
            if (timestampValidationResult != null) {
                boolean reserved = replayProtector.reserve(document.getDocumentElement().getAttribute(ATTRIBUTE_ID),
                                                           assertion.getAttribute(ATTRIBUTE_ID),
                                                           timestampValidationResult.replayCacheDeadline());
                if (!reserved) {
                    throw Exceptions.createHandled()
                                    .withSystemErrorMessage(
                                            "Invalid SAML Response: Response has already been processed.")
                                    .handle();
                }
            }

            return parseAssertion(assertion, fingerprint);
        } catch (HandledException exception) {
            throw exception;
        } catch (Exception exception) {
            throw Exceptions.handle()
                            .to(LOG)
                            .error(exception)
                            .withSystemErrorMessage("An error occurred while parsing a SAML Response: %s (%s)")
                            .handle();
        }
    }

    /**
     * Selects the element with the given node name.
     * <p>
     * This method ensures that there is only one element, as we want to ensure that there is only one <tt>Assertion</tt> and
     * one <tt>Signature</tt> for it.
     *
     * @param document  the XML document
     * @param namespace the optional namespace URI
     * @param nodeName  the name of the node
     * @return the element with the given name
     * @throws HandledException if there are either no or multiple nodes of the given name
     */
    private Element selectSingleElement(Document document, @Nullable String namespace, String nodeName) {
        NodeList nodes = Strings.isFilled(namespace) ?
                         document.getElementsByTagNameNS(namespace, nodeName) :
                         document.getElementsByTagName(nodeName);
        if (nodes.getLength() != 1) {
            LOG.FINE("SAML Response has %s elements of type: %s", nodes.getLength(), nodeName);
            throw Exceptions.createHandled()
                            .withSystemErrorMessage("Invalid SAML Response: Expected exactly one %s!", nodeName)
                            .handle();
        }

        return (Element) nodes.item(0);
    }

    /**
     * Verifies the time constraints within the <tt>Assertion</tt>.
     *
     * @param assertion the assertion to verify
     * @return the effective deadline until which the assertion is considered replayable
     */
    private TimestampValidationResult verifyTimestamp(Element assertion) {
        Instant now = Instant.now();
        String issueInstantValue = assertion.getAttribute(ATTRIBUTE_ISSUE_INSTANT);
        if (Strings.isEmpty(issueInstantValue)) {
            throw invalidTimestamp(ATTRIBUTE_ISSUE_INSTANT, "<missing>");
        }

        Instant issueInstant = parseRequiredInstant(ATTRIBUTE_ISSUE_INSTANT, issueInstantValue);

        if (issueInstant.isAfter(now.plus(SAML_CLOCK_SKEW))) {
            throw invalidTimestamp(ATTRIBUTE_ISSUE_INSTANT, DateTimeFormatter.ISO_INSTANT.format(issueInstant));
        }

        Optional<Instant> notBefore = extractConditionsTimestamp(assertion, ATTRIBUTE_NOT_BEFORE);
        if (notBefore.isPresent() && notBefore.get().isAfter(now.plus(SAML_CLOCK_SKEW))) {
            throw invalidTimestamp(ATTRIBUTE_NOT_BEFORE, DateTimeFormatter.ISO_INSTANT.format(notBefore.get()));
        }

        Instant notOnOrAfter = extractEffectiveNotOnOrAfter(assertion);
        Duration effectiveMaxResponseAcceptanceDuration =
                determineEffectiveMaxResponseAcceptanceDuration(maxResponseAcceptanceDuration);
        Instant cappedNotOnOrAfter = min(notOnOrAfter, issueInstant.plus(effectiveMaxResponseAcceptanceDuration));
        Instant deadline = cappedNotOnOrAfter.plus(SAML_CLOCK_SKEW);
        if (!now.isBefore(deadline)) {
            if (cappedNotOnOrAfter.equals(notOnOrAfter)) {
                throw invalidTimestamp(ATTRIBUTE_NOT_ON_OR_AFTER, DateTimeFormatter.ISO_INSTANT.format(notOnOrAfter));
            }

            throw Exceptions.createHandled()
                            .withSystemErrorMessage(
                                    "Invalid SAML Response: Assertion is older than the maximum acceptance duration of %s.",
                                    effectiveMaxResponseAcceptanceDuration)
                            .handle();
        }

        return new TimestampValidationResult(deadline);
    }

    /**
     * Returns the earlier of the given instants.
     *
     * @param first  the first instant to compare
     * @param second the second instant to compare
     * @return the earlier instant
     */
    private Instant min(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    /**
     * Represents the result of SAML timestamp validation.
     *
     * @param replayCacheDeadline the timestamp until which the response and assertion IDs must remain blocked
     */
    private record TimestampValidationResult(Instant replayCacheDeadline) {
    }

    /**
     * Parses an ISO timestamp from a required SAML attribute value.
     *
     * @param attributeName the name of the SAML attribute being parsed
     * @param value         the raw attribute value
     * @return the parsed timestamp
     */
    private Instant parseRequiredInstant(String attributeName, String value) {
        if (Strings.isEmpty(value)) {
            throw Exceptions.createHandled()
                            .withSystemErrorMessage("Invalid SAML Response: Missing %s.", attributeName)
                            .handle();
        }

        try {
            return Instant.from(DateTimeFormatter.ISO_INSTANT.parse(value));
        } catch (DateTimeParseException _) {
            throw invalidTimestamp(attributeName, value);
        }
    }

    /**
     * Extracts a timestamp attribute from the optional <tt>Conditions</tt> element.
     *
     * @param assertion     the assertion to inspect
     * @param attributeName the timestamp attribute to extract
     * @return the parsed timestamp or an empty optional if the attribute is absent
     */
    private Optional<Instant> extractConditionsTimestamp(Element assertion, String attributeName) {
        NodeList conditionsElements = assertion.getElementsByTagNameNS(SAML_NAMESPACE, "Conditions");
        if (conditionsElements.getLength() > 1) {
            throw Exceptions.createHandled()
                            .withSystemErrorMessage("Invalid SAML Response: Expected at most one Conditions element.")
                            .handle();
        }

        if (conditionsElements.getLength() == 0) {
            return Optional.empty();
        }

        Element conditions = (Element) conditionsElements.item(0);
        String value = conditions.getAttribute(attributeName);
        if (Strings.isEmpty(value)) {
            return Optional.empty();
        }

        return Optional.of(parseRequiredInstant(attributeName, value));
    }

    /**
     * Extracts the earliest effective <tt>NotOnOrAfter</tt> deadline from assertion conditions.
     *
     * @param assertion the assertion to inspect
     * @return the effective deadline after which the assertion is no longer valid
     */
    private Instant extractEffectiveNotOnOrAfter(Element assertion) {
        Optional<Instant> conditionsNotOnOrAfter = extractConditionsTimestamp(assertion, ATTRIBUTE_NOT_ON_OR_AFTER);
        Optional<Instant> subjectConfirmationNotOnOrAfter = extractEarliestSubjectConfirmationNotOnOrAfter(assertion);

        if (conditionsNotOnOrAfter.isEmpty() && subjectConfirmationNotOnOrAfter.isEmpty()) {
            throw Exceptions.createHandled()
                            .withSystemErrorMessage("Invalid SAML Response: Missing %s.", ATTRIBUTE_NOT_ON_OR_AFTER)
                            .handle();
        }

        if (conditionsNotOnOrAfter.isEmpty()) {
            return subjectConfirmationNotOnOrAfter.get();
        }

        return subjectConfirmationNotOnOrAfter.filter(instant -> !conditionsNotOnOrAfter.get().isBefore(instant))
                                              .orElseGet(conditionsNotOnOrAfter::get);
    }

    /**
     * Extracts the earliest <tt>NotOnOrAfter</tt> timestamp from all <tt>SubjectConfirmationData</tt> elements.
     *
     * @param assertion the assertion to inspect
     * @return the earliest timestamp or an empty optional if no subject confirmation deadline is present
     */
    private Optional<Instant> extractEarliestSubjectConfirmationNotOnOrAfter(Element assertion) {
        NodeList subjectConfirmationDataElements =
                assertion.getElementsByTagNameNS(SAML_NAMESPACE, "SubjectConfirmationData");
        Optional<Instant> earliestTimestamp = Optional.empty();

        for (int i = 0; i < subjectConfirmationDataElements.getLength(); i++) {
            Element subjectConfirmationData = (Element) subjectConfirmationDataElements.item(i);
            String notOnOrAfter = subjectConfirmationData.getAttribute(ATTRIBUTE_NOT_ON_OR_AFTER);
            if (Strings.isFilled(notOnOrAfter)) {
                Instant timestamp = parseRequiredInstant(ATTRIBUTE_NOT_ON_OR_AFTER, notOnOrAfter);
                if (earliestTimestamp.isEmpty() || timestamp.isBefore(earliestTimestamp.get())) {
                    earliestTimestamp = Optional.of(timestamp);
                }
            }
        }

        return earliestTimestamp;
    }

    /**
     * Creates a handled exception for an invalid SAML timestamp.
     *
     * @param attributeName the timestamp attribute that failed validation
     * @param value         the invalid value to report
     * @return the handled exception to throw
     */
    private HandledException invalidTimestamp(String attributeName, String value) {
        return Exceptions.createHandled()
                         .withSystemErrorMessage("Invalid SAML Response: Invalid %s: %s", attributeName, value)
                         .handle();
    }

    /**
     * Extracts all relevant data from the <tt>Assertion</tt>.
     *
     * @param assertion   the assertion to parse
     * @param fingerprint the fingerprint of the X509 certificate which was used to sign the assertion
     * @return the response which represents the payload of the <tt>Assertion</tt>
     */
    private SamlResponse parseAssertion(Element assertion, String fingerprint) {
        StructuredNode node = StructuredNode.of(assertion);
        MultiMap<String, String> attributes = MultiMap.create();

        for (StructuredNode attribute : node.queryNodeList(
                "*[local-name()='AttributeStatement']/*[local-name()='Attribute']")) {
            for (StructuredNode attributeValue : attribute.queryNodeList("*[local-name()='AttributeValue']")) {
                attributes.put(attribute.queryString("@Name"), attributeValue.queryString("."));
            }
        }

        return new SamlResponse(node.queryString("*[local-name()='Issuer']"),
                                fingerprint,
                                node.queryString("*[local-name()='Subject']/*[local-name()='NameID']"),
                                attributes);
    }

    /**
     * Parses the given SAML response XML into a hardened DOM document.
     *
     * @param inputStream the XML stream to parse
     * @return the parsed DOM document
     * @throws SAXException                 in case the XML cannot be parsed
     * @throws IOException                  in case the stream cannot be read
     * @throws ParserConfigurationException in case the XML parser cannot be configured securely
     */
    private Document getResponseDocument(InputStream inputStream)
            throws SAXException, IOException, ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature(FEATURE_DISALLOW_DOCTYPE_DECL, true);
        factory.setFeature(FEATURE_LOAD_EXTERNAL_DTD, false);
        factory.setFeature(FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
        factory.setFeature(FEATURE_EXTERNAL_PARAMETER_ENTITIES, false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(inputStream);
    }

    /**
     * Validates the <tt>Signature</tt> in the document.
     * <p>
     * This also ensures, that there is only one signature and that the signature actually signs the given
     * <tt>Assertion</tt> and not anything else.
     *
     * @param document  the XML document
     * @param assertion the assertion which must be signed
     * @return the fingerprint of the X509 certificate which was used to generate the signature
     * @throws Exception in case an XML or signing error occurs
     */
    private String validateXMLSignature(Document document, Element assertion) throws Exception {
        assertion.setIdAttribute(ATTRIBUTE_ID, true);
        String idToVerify = assertion.getAttribute(ATTRIBUTE_ID);

        Element signatureElement = selectSingleElement(document, XMLSignature.XMLNS, "Signature");

        XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
        DOMValidateContext valContext = new DOMValidateContext(new KeyValueKeySelector(), signatureElement);
        valContext.setProperty(XML_SIGNATURE_SECURE_VALIDATION, Boolean.TRUE);
        XMLSignature signature = factory.unmarshalXMLSignature(valContext);

        if (!Strings.areEqual(getReferenceBeingSigned(signature), "#" + idToVerify)) {
            LOG.FINE("SAML Response doesn't sign the assertion. Reference: %s, Assertion-ID: %s",
                     getReferenceBeingSigned(signature),
                     idToVerify);
            throw Exceptions.createHandled()
                            .withSystemErrorMessage(
                                    "Invalid SAML Response: The given Signature doesn't sign the given Assertion.")
                            .handle();
        }

        if (!signature.validate(valContext)) {
            LOG.FINE("SAML Response contains an invalid signature!");
            throw Exceptions.createHandled()
                            .withSystemErrorMessage("Invalid SAML Response: The given Signature isn't valid.")
                            .handle();
        }

        X509Certificate certificate = ((X509CertificateResult) signature.getKeySelectorResult()).certificate();
        return Hasher.sha1().hashBytes(certificate.getEncoded()).toHexString().toLowerCase();
    }

    /**
     * Obtains the reference of the given signature
     *
     * @param signature the signature to parse
     * @return the effective reference URI
     */
    @SuppressWarnings({"squid:S1905", "RedundantCast"})
    @Nonnull
    @Explain("The cast helps the type-interference of the compiler - otherwise it sometimes reports an error")
    private String getReferenceBeingSigned(XMLSignature signature) {
        return ((List<Reference>) signature.getSignedInfo().getReferences()).stream()
                                                                            .findFirst()
                                                                            .map(Reference::getURI)
                                                                            .orElse("");
    }

    /**
     * Used to extract the inlined X509 certificate from within the signature.
     */
    private static class KeyValueKeySelector extends KeySelector {

        @Override
        public KeySelectorResult select(KeyInfo keyInfo,
                                        KeySelector.Purpose purpose,
                                        AlgorithmMethod method,
                                        XMLCryptoContext cryptoContext) throws KeySelectorException {
            if (keyInfo == null) {
                throw Exceptions.createHandled()
                                .withSystemErrorMessage("Invalid SAML Response: Signature doesn't contain a KeyInfo!")
                                .handle();
            }

            for (XMLStructure xmlStructure : keyInfo.getContent()) {
                if (xmlStructure instanceof X509Data x509Data && !x509Data.getContent().isEmpty()) {
                    Optional<X509Certificate> optionalCertificate = x509Data.getContent()
                                                                            .stream()
                                                                            .filter(X509Certificate.class::isInstance)
                                                                            .map(X509Certificate.class::cast)
                                                                            .findFirst();
                    if (optionalCertificate.isPresent()) {
                        return new X509CertificateResult(optionalCertificate.get());
                    }
                }
            }

            throw Exceptions.createHandled()
                            .withSystemErrorMessage(
                                    "Invalid SAML Response: Signature doesn't contain a valid signing key (X509 certificate)!")
                            .handle();
        }
    }

    /**
     * Represents the inlined X509 certificate from within the signature.
     */
    private record X509CertificateResult(X509Certificate certificate) implements KeySelectorResult {

        @Override
        public Key getKey() {
            return certificate.getPublicKey();
        }
    }
}
