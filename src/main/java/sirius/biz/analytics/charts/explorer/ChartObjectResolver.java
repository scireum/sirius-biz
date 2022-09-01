/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts.explorer;

import sirius.kernel.di.std.AutoRegister;
import sirius.kernel.di.std.Named;

import java.util.Optional;

/**
 * Responsible to resolving and suggesting objects/entities for one or more {@link ChartFactory}.
 *
 * @param <O> the type of objects being resolved
 */
@AutoRegister
public interface ChartObjectResolver<O> extends Named {

    /**
     * Obtains the identifier used to represent the given object.
     *
     * @param object the object to marshal
     * @return a representation which can be resolved by {@link #resolve(String)}
     */
    String fetchIdentifier(O object);

    /**
     * Resolves an identifier created by {@link #fetchIdentifier(Object)} into the actual object.
     * <p>
     * Note that this method is in charge of performing permission/access checks!
     *
     * @param identifier the identifier to resolve
     * @return the resolved object wrapped as optional or an empty optional if no matching object is present or if the
     * resolved object isn't accessible to the current user
     */
    Optional<O> resolve(String identifier);

    /**
     * Specifies the autocomplete URI used to suggest entities.
     *
     * @return the autocomplete URI which provides suggestions. Note that the suggestions must contain identifiers
     * which can be resolved by {@link #resolve(String)}.
     */
    String autocompleteUri();
}
