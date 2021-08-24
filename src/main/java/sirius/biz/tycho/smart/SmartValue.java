/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.smart;

/**
 * Describes a "smart value" which is kind of an annotation or extra information for an entity.
 * <p>
 * Smart values are provided by {@link SmartValueProvider SmartValueProviders} and can provide a link or action along
 * with a text to copy to the clipboard.
 * <p>
 * This can be e.g. used to reveal the phone number or email address for a user listed in a card or table.
 */
public record SmartValue(String icon, String label, String action, String copyPayload) {
}
