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
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;

import javax.annotation.Nonnull;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Validates that the stored value in a string property is a valid telephone number.
 */
@Register
public class PhoneNumberValidator implements PropertyValidator {

    /**
     * Matches a part of a phone number like <tt>55 55</tt> or <tt>55</tt>.
     */
    private static final String NUMERIC_PART = "( *\\d+)*+";

    /**
     * Validates a phone number.
     */
    public static final Pattern VALID_PHONE_NUMBER =
            Pattern.compile("\\+?\\d+" + NUMERIC_PART + "( */" + NUMERIC_PART + ")?( *-" + NUMERIC_PART + ")?");

    @Override
    public void validate(BaseEntity<?> entity, Property property, Object value, Consumer<String> validationConsumer) {
        if (value instanceof String phoneNumber && Strings.isFilled(phoneNumber) && !VALID_PHONE_NUMBER.matcher(
                phoneNumber).matches()) {
            validationConsumer.accept(createInvalidPhoneException(property, phoneNumber).getMessage());
        }
    }

    @Override
    public void beforeSave(BaseEntity<?> entity, Property property, Object value) {
        if (value instanceof String phoneNumber && Strings.isFilled(phoneNumber) && !VALID_PHONE_NUMBER.matcher(
                phoneNumber).matches()) {
            throw createInvalidPhoneException(property, phoneNumber);
        }
    }

    private HandledException createInvalidPhoneException(Property property, String value) {
        return Exceptions.createHandled()
                         .error(new InvalidFieldException(property.getName()))
                         .withNLSKey("ContactData.invalidPhone")
                         .set("field", property.getLabel())
                         .set("value", value)
                         .handle();
    }

    @Nonnull
    @Override
    public String getName() {
        return "phone-number-validator";
    }
}
