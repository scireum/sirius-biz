/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.flags;

import sirius.kernel.commons.MultiMap;
import sirius.kernel.commons.Strings;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;

import java.util.Optional;
import java.util.stream.Stream;

public class PerformanceFlag {

    private static final MultiMap<Class<?>, PerformanceFlag> flagsPerType = MultiMap.createOrdered();

    private Class<?> targetType;

    private String name;

    private boolean visible;

    private boolean filterable;

    private int bitIndex;

    private PerformanceFlag() {

    }

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

    public static Stream<PerformanceFlag> flagsOfType(Class<?> aClass) {
        return flagsPerType.get(aClass).stream();
    }

    public static Optional<PerformanceFlag> flagWithName(Class<?> aClass, String name) {
        if (Strings.isEmpty(name)) {
            return Optional.empty();
        }

        return flagsOfType(aClass).filter(flag -> Strings.areEqual(name, flag.getName())).findFirst();
    }

    public PerformanceFlag makeVisible() {
        this.visible = true;
        return this;
    }

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
