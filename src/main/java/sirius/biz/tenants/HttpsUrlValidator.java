/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.InvalidFieldException;
import sirius.db.mixing.Property;
import sirius.db.mixing.PropertyValidator;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Urls;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * Validates that the stored value in a string property is a valid HTTPS URL.
 */
@Register
public class HttpsUrlValidator implements PropertyValidator {

    @Override
    public void validate(BaseEntity<?> entity, Property property, Object value, Consumer<String> validationConsumer) {
        if (value instanceof String url && Strings.isFilled(url) && !Urls.isHttpsUrl(url)) {
            validationConsumer.accept(createInvalidHttpsUrlException(property, url).getMessage());
        }
    }

    @Override
    public void beforeSave(BaseEntity<?> entity, Property property, Object value) {
        if (value instanceof String url && Strings.isFilled(url) && !Urls.isHttpsUrl(url)) {
            throw createInvalidHttpsUrlException(property, url);
        }
    }

    private HandledException createInvalidHttpsUrlException(Property property, String value) {
        return Exceptions.createHandled()
                         .error(new InvalidFieldException(property.getName()))
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
