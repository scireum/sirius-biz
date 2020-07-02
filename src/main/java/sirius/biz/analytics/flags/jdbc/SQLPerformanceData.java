/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.flags.jdbc;

import sirius.biz.analytics.flags.PerformanceData;
import sirius.biz.analytics.flags.PerformanceFlag;
import sirius.biz.analytics.flags.PerformanceFlagModifier;
import sirius.biz.analytics.flags.PerformanceFlagged;
import sirius.biz.web.SQLPageHelper;
import sirius.db.jdbc.SQLEntity;
import sirius.db.jdbc.constraints.SQLConstraint;
import sirius.db.mixing.annotations.TranslationSource;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Facet;

/**
 * Can be embedded into a {@link SQLEntity} in order to record / toggle performance flags for it.
 */
@TranslationSource(PerformanceData.class)
public class SQLPerformanceData extends PerformanceData {

    private static final String FACET_NAME_PERFORMANCE_FLAGS = "performanceFlag";

    protected long flags;

    /**
     * Creeates a new instance for the given entity.
     *
     * @param owner the entity in which this composite is embedded
     */
    public SQLPerformanceData(SQLEntity owner) {
        super(owner);
    }

    @Override
    public PerformanceFlagModifier modify() {
        return new SQLPerformanceFlagModifier(this);
    }

    @Override
    public boolean isSet(PerformanceFlag flag) {
        return (flags & (1L << flag.getBitIndex())) != 0;
    }

    /**
     * Creates a constraint to filter on entities which have the given performance flag set.
     *
     * @param flag the flag to check
     * @return a constraint which can be added to {@link sirius.db.jdbc.SmartQuery#where(SQLConstraint)}
     */
    public static SQLConstraint filterFlagSet(PerformanceFlag flag) {
        return new PerformanceFlagConstraint(flag, true);
    }

    /**
     * Creates a constraint to filter on entities which have the given performance flag cleared.
     *
     * @param flag the flag to check
     * @return a constraint which can be added to {@link sirius.db.jdbc.SmartQuery#where(SQLConstraint)}
     */
    public static SQLConstraint filterFlagClear(PerformanceFlag flag) {
        return new PerformanceFlagConstraint(flag, false);
    }

    /**
     * Creates a filter facet which permits to filter on performance flags.
     *
     * @param pageHelper the helper to append the facet to
     */
    public static void addFilterFacet(SQLPageHelper<? extends PerformanceFlagged> pageHelper) {
        Facet facet = new Facet(NLS.get("PerformanceData.flags"), FACET_NAME_PERFORMANCE_FLAGS, null, null);
        Class<?> type = pageHelper.getBaseQuery().getDescriptor().getType();

        pageHelper.addFacet(facet, (currentFacet, query) -> {
            PerformanceFlag.flagWithName(type, currentFacet.getValue())
                           .ifPresent(flag -> query.where(filterFlagSet(flag)));
        });

        PerformanceFlag.flagsOfType(type).filter(PerformanceFlag::isFilterable).forEach(flag -> {
            facet.addItem(flag.getName(), flag.getLabel(), -1);
        });
    }
}
