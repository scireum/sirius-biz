/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.flags;

import sirius.biz.analytics.flags.jdbc.SQLPerformanceData;
import sirius.biz.analytics.flags.mongo.MongoPerformanceData;
import sirius.biz.web.MongoPageHelper;
import sirius.biz.web.SQLPageHelper;
import sirius.kernel.commons.MultiMap;
import sirius.kernel.commons.Strings;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Defines the required metadata for a performance flag.
 * <p>
 * A performance flag can be toggled for the entities it was defined for and thus record things like "this is an
 * active user", "this is a large object" etc. As we use an optimized representation, each performance flag must be
 * known ahead of time (declared as constant).
 */
public class PerformanceFlag {

    private static final MultiMap<Class<?>, PerformanceFlag> flagsPerType = MultiMap.createOrdered();

    private Class<?> targetType;

    private String name;

    private boolean visible;

    private boolean filterable;

    private int bitIndex;

    private PerformanceFlag() {

    }

    /**
     * Registers a new performance flag for the given entity type.
     * <p>
     * This must be done when initializing a constant as a performance flag has to be unique.
     *
     * @param targetType the target type for which this flag can be toggled
     * @param name       the name of the flag
     * @param bitIndex   the bit index to use. This has to be a unique index per type with a value ranging form 0 to 63.
     *                   Note that 0 to 15 are reseverd for sirius libraries, therefore an application can define up to
     *                   48 custom flags per entity type
     * @return the performance flag which can be used for toggeling and filtering
     */
    public static PerformanceFlag register(Class<?> targetType, String name, int bitIndex) {
        PerformanceFlag result = new PerformanceFlag();
        result.targetType = targetType;
        result.name = name;
        result.bitIndex = bitIndex;

        synchronized (flagsPerType) {
            ensureUniqueName(targetType, name);
            ensureUniqueBitIndex(targetType, name, bitIndex);

            flagsPerType.put(targetType, result);
        }

        return result;
    }

    protected static void ensureUniqueName(Class<?> targetType, String name) {
        flagsPerType.get(targetType)
                    .stream()
                    .filter(otherFlag -> Strings.areEqual(name, otherFlag.name))
                    .findFirst()
                    .ifPresent(collision -> Exceptions.handle()
                                                      .to(Log.SYSTEM)
                                                      .withSystemErrorMessage(
                                                              "A flag with name %s has already been registered for %s!",
                                                              name,
                                                              targetType)
                                                      .handle());
    }

    protected static void ensureUniqueBitIndex(Class<?> targetType, String name, int bitIndex) {
        flagsPerType.get(targetType)
                    .stream()
                    .filter(otherFlag -> bitIndex == otherFlag.bitIndex)
                    .findFirst()
                    .ifPresent(collision -> Exceptions.handle()
                                                      .to(Log.SYSTEM)
                                                      .withSystemErrorMessage(
                                                              "A flag (%s) with name %s has already the same bit index assigned (%s) for %s!",
                                                              collision.getName(),
                                                              name,
                                                              bitIndex,
                                                              targetType)
                                                      .handle());
    }

    /**
     * Returns a stream of all tags registered for a given entity type.
     *
     * @param aClass the entity type to resolve the flags for
     * @return a stream of all known flags for the given type
     */
    public static Stream<PerformanceFlag> flagsOfType(Class<?> aClass) {
        return flagsPerType.get(aClass).stream();
    }

    /**
     * Returns the flag with the given name.
     *
     * @param aClass the entity to fetch the flag for
     * @param name   the name of the flag
     * @return the flag wrapped as optional or an empty optional if no flag with the given name exists
     */
    public static Optional<PerformanceFlag> flagWithName(Class<?> aClass, String name) {
        if (Strings.isEmpty(name)) {
            return Optional.empty();
        }

        return flagsOfType(aClass).filter(flag -> Strings.areEqual(name, flag.getName())).findFirst();
    }

    /**
     * Makes this performance flag visible in the UI.
     * <p>
     * By default a performance flag is hidden from the user and only used internally. However, there are good reasons
     * to make a lot of perfromance flags visible.
     *
     * @return the flag itself for fluent method calls
     */
    public PerformanceFlag makeVisible() {
        this.visible = true;
        return this;
    }

    /**
     * Marks this performance flag as recommended for filtering.
     * <p>
     * If marked, a flag will show up as facet filter.
     *
     * @return the flag itself for fluent method calls
     * @see SQLPerformanceData#addFilterFacet(SQLPageHelper)
     * @see MongoPerformanceData#addFilterFacet(MongoPageHelper)
     */
    public PerformanceFlag markAsFilter() {
        this.filterable = true;
        return this;
    }

    public Class<?> getTargetType() {
        return targetType;
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return NLS.getIfExists("PerformanceFlag." + name, null).orElse(name);
    }

    public int getBitIndex() {
        return bitIndex;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isFilterable() {
        return filterable;
    }
}
