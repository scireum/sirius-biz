/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.smart;

import sirius.kernel.di.std.Priorized;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Provides one or more {@link SmartValue smart values} for a given payload.
 * <p>
 * It can also derive other payloads to trigger additional smart value providers. This is e.g. used to derive the
 * email address from a user account so that the {@link EmailSmartValueProvider} is triggered.
 */
public interface SmartValueProvider extends Priorized {

    /**
     * Reports all smart values available for the given payload and type.
     *
     * @param type           the type of the payload. This is mostly used, if the class of the payload doesn't specify the type
     *                       by itself (e.g. an email address is technically just a String).
     * @param payload        the payload to generate one or more smart values for
     * @param valueCollector the collector to be supplied with smart values
     */
    void collectValues(String type, Object payload, Consumer<SmartValue> valueCollector);

    /**
     * Permits deriving other payloads from a given one.
     * <p>
     * This is mostly done for code-reuse. As an example, the {@link UserAccountSmartValueProvider} derives the
     * email address as additional smart value, which will then trigger the {@link EmailSmartValueProvider}.
     *
     * @param type                       the type of the payload to derive values for
     * @param payload                    the payload itself
     * @param derivedSmartValueCollector the collector to be supplied with additional "type/payload" pairs.
     */
    void deriveSmartValues(String type, Object payload, BiConsumer<String, Object> derivedSmartValueCollector);
}
