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
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.web.mails.Mails;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * Validates that the stored value in a string property is a valid email address.
 */
@Register
public class EmailAddressValidator implements PropertyValidator {

    @Part
    private static Mails mails;

    @Override
    public void validate(BaseEntity<?> entity, Property property, Object value, Consumer<String> validationConsumer) {
        // Nothing to do here ...
    }

    @Override
    public void beforeSave(BaseEntity<?> entity, Property property, Object value) {
        if (value instanceof String email && Strings.isFilled(email) && !mails.isValidMailAddress(email, null)) {
            throw Exceptions.createHandled()
                            .error(new InvalidFieldException(property.getName()))
                            .withNLSKey("MailService.invalidAddress")
                            .set("address", email)
                            .handle();
        }
    }

    @Nonnull
    @Override
    public String getName() {
        return "email-address-validator";
    }
}
