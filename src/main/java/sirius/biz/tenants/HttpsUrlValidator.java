/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.db.mixing.Property;
import sirius.db.mixing.PropertyValidator;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;

import javax.annotation.Nonnull;
import java.net.URI;
import java.net.URL;
import java.util.function.Consumer;

/**
 * Validates that the stored value in a string property is a valid HTTPS URL.
 */
@Register
public class HttpsUrlValidator implements PropertyValidator {

    @Override
    public void validate(Property property, Object value, Consumer<String> validationConsumer) {
        try {
            parseHttpsUrl(property, value);
        } catch (Exception exception) {
            validationConsumer.accept(exception.getMessage());
        }
    }

    @Override
    public void beforeSave(Property property, Object value) {
        parseHttpsUrl(property, value);
    }

    /**
     * Parses the given value into a URL and makes sure it is a valid HTTPS URL.
     *
     * @param property the property to parse
     * @param value    the value to parse
     * @return the parsed URL
     * @see #parseUrl(Property, Object)
     * @see #createInvalidHttpsUrlException(Property, String)
     */
    private URL parseHttpsUrl(Property property, Object value) {
        URL url = parseUrl(property, value);
        if (url == null) {
            return null;
        }

        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw createInvalidHttpsUrlException(property, url.toString());
        }

        return url;
    }

    /**
     * Parses the given value into a URL. Effectively, this method wraps standard methods to throw a handled exception
     * with a localized error message if the URL is invalid.
     *
     * @param property the property to parse
     * @param value    the value to parse
     * @return the parsed URL
     * @see #createInvalidHttpsUrlException(Property, String)
     */
    private URL parseUrl(Property property, Object value) {
        if (value instanceof String url && Strings.isFilled(url)) {
            try {
                return URI.create(url).toURL();
            } catch (Exception exception) {
                throw createInvalidHttpsUrlException(property, url);
            }
        }

        return null;
    }

    private HandledException createInvalidHttpsUrlException(Property property, String value) {
        return Exceptions.createHandled()
                         .withNLSKey("HttpsUrlValidator.invalidHttpsUrl")
                         .set("field", property.getLabel())
                         .set("value", value)
                         .handle();
    }

    @Nonnull
    @Override
    public String getName() {
        return "https-url-validator";
    }
}
