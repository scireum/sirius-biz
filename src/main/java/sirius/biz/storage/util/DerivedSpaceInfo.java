/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.util;

import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.settings.Extension;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Derives and caches a value per storage space.
 *
 * @param <I> the type of the derived value.
 */
public class DerivedSpaceInfo<I> {

    @Part
    private static StorageUtils utils;

    private String description;
    private StorageUtils.ConfigScope scope;
    private Function<Extension, I> computer;
    private Predicate<Extension> filter;
    private Map<String, I> cache;

    /**
     * Creates a new info with the given description, scope and value computer.
     *
     * @param description the description of the computed values. This is used to generate proper error messages.
     * @param scope       the scope used to lookup the correct config block
     * @param computer    the function used to compute the actual value
     */
    public DerivedSpaceInfo(String description, StorageUtils.ConfigScope scope, Function<Extension, I> computer) {
        this.description = description;
        this.scope = scope;
        this.computer = computer;
    }

    /**
     * Creates a new info with the given description, scope and value computer.
     *
     * @param description the description of the computed values. This is used to generate proper error messages.
     * @param scope       the scope used to lookup the correct config block
     * @param filter      the filter expression used to determine if the given config block should be considered or not
     * @param computer    the function used to compute the actual value
     */
    public DerivedSpaceInfo(String description,
                            StorageUtils.ConfigScope scope,
                            Predicate<Extension> filter,
                            Function<Extension, I> computer) {
        this.description = description;
        this.scope = scope;
        this.filter = filter;
        this.computer = computer;
    }

    /**
     * Returns the computed value for the given storage space.
     * <p>
     * Note that all values are computed once the first request is received and then cached for further use.
     *
     * @param space the space for which the value was requested
     * @return the requested value
     * @throws sirius.kernel.health.HandledException if no proper configuration is present
     */
    public I get(String space) {
        if (cache == null) {
            cache = fill();
        }

        I info = cache.get(space);
        if (info == null) {
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .withSystemErrorMessage("Cannot derive required information (%s) for space '%s'"
                                                    + " in scope '%s'. Please verify the system configuration.",
                                                    description,
                                                    space,
                                                    scope)
                            .handle();
        }
        return info;
    }

    private synchronized Map<String, I> fill() {
        Stream<Extension> stream = utils.getStorageSpaces(scope).stream();
        if (filter != null) {
            stream = stream.filter(filter);
        }

        return stream.collect(Collectors.toMap(Extension::getId, computer));
    }

    /**
     * Determines if a value is present for the given space.
     *
     * @param space the space to check
     * @return <tt>true</tt> if a value is present, <tt>false</tt> otherwise
     */
    public boolean contains(String space) {
        if (cache == null) {
            cache = fill();
        }
        return cache.containsKey(space);
    }
}
