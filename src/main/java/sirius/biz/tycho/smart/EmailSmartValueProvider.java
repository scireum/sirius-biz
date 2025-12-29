/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.smart;

import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Urls;
import sirius.kernel.di.std.Register;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Provides a "mailto" link for email addresses.
 */
@Register
public class EmailSmartValueProvider implements SmartValueProvider {

    /**
     * Defines the type understood by this provider.
     */
    public static final String VALUE_TYPE_EMAIL = "email";

    @Override
    public void collectValues(String type, Object payload, Consumer<SmartValue> valueCollector) {
        if (Strings.isFilled(payload) && VALUE_TYPE_EMAIL.equals(type)) {
            String email = payload.toString();
            valueCollector.accept(new SmartValue("fa-solid fa-envelope", email, "mailto:" + Urls.encode(email), email));
        }
    }

    @Override
    public void deriveSmartValues(String type, Object payload, BiConsumer<String, Object> derivedSmartValueCollector) {
        // No values are derived
    }

    @Override
    public int getPriority() {
        return 50;
    }
}
