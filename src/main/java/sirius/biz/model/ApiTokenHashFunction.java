/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.kernel.di.std.Priorized;

/**
 * Describes a way to hash {@link LoginData#API_TOKEN apiTokens}.
 */
public interface ApiTokenHashFunction extends Priorized {

    /**
     * Compute the hashed apiToken has for the given time.
     *
     * @param apiToken the apiToken which should be hashed
     * @param time     the time for which the apiToken should be valid
     * @return the apiToken hashed with the current time
     */
    String computeHash(String apiToken, long time);
}
