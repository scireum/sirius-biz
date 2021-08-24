/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.smart;

import sirius.kernel.di.std.Priorized;

import java.util.Optional;

/**
 * Used to resolve a given payload type and data into a proper object.
 * <p>
 * As the smart value data is loaded per AJAX, the client can only send strings to the server, which then might
 * be needed to be converted into a proper object. This is done by a <tt>SmartValueResolver</tt>.
 *
 * @param <T> the type of objects being resolved by this resolver
 */
public interface SmartValueResolver<T> extends Priorized {

    /**
     * Attempts to resolve the given type and payload into an object.
     *
     * @param type    the type of the payload
     * @param payload the string version of the payload
     * @return the resolved object or an empty optional if this resolver can't resolve the given data into an object
     */
    Optional<T> tryResolve(String type, String payload);
}
