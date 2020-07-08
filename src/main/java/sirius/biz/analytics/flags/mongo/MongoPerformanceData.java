/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.flags.mongo;

import sirius.biz.analytics.flags.PerformanceData;
import sirius.biz.analytics.flags.PerformanceFlag;
import sirius.biz.analytics.flags.PerformanceFlagModifier;
import sirius.biz.analytics.flags.PerformanceFlagged;
import sirius.biz.web.MongoPageHelper;
import sirius.db.jdbc.constraints.SQLConstraint;
import sirius.db.mixing.annotations.TranslationSource;
import sirius.db.mixing.types.StringList;
import sirius.db.mongo.MongoEntity;
import sirius.db.mongo.QueryBuilder;
import sirius.db.mongo.constraints.MongoConstraint;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Facet;

/**
 * Can be embedded into a {@link MongoEntity} in order to record / toggle performance flags for it.
 */
@TranslationSource(PerformanceData.class)
public class MongoPerformanceData extends PerformanceData {

    private static final String FACET_NAME_PERFORMANCE_FLAGS = "performanceFlag";

    protected final StringList flags = new StringList();

    /**
     * Creeates a new instance for the given entity.
     *
     * @param owner the entity in which this composite is embedded
     */
    public MongoPerformanceData(MongoEntity owner) {
        super(owner);
    }

    @Override
    public PerformanceFlagModifier modify() {
        return new MongoPerformanceFlagModifier(this);
    }

    @Override
    public boolean isSet(PerformanceFlag flag) {
        return flags.contains(String.valueOf(flag.getBitIndex()));
    }

    /**
     * Creates a constraint to filter on entities which have the given performance flag set.
     *
     * @param flag the flag to check
     * @return a constraint which can be added to {@link sirius.db.mongo.MongoQuery#where(MongoConstraint)}
     */
    public static MongoConstraint filterFlagSet(PerformanceFlag flag) {
        return QueryBuilder.FILTERS.eq(PerformanceFlagged.PERFORMANCE_DATA.inner(FLAGS),
                                       String.valueOf(flag.getBitIndex()));
    }

    /**
     * Creates a constraint to filter on entities which have the given performance flag cleared.
     *
     * @param flag the flag to check
     * @return a constraint which can be added to {@link sirius.db.mongo.MongoQuery#where(MongoConstraint)}
     */
    public static MongoConstraint filterFlagClear(PerformanceFlag flag) {
        return QueryBuilder.FILTERS.ne(PerformanceFlagged.PERFORMANCE_DATA.inner(FLAGS),
                                       String.valueOf(flag.getBitIndex()));
    }

    /**
     * Creates a filter facet which permits to filter on performance flags.
     *
     * @param pageHelper the helper to append the facet to
     */
    public static void addFilterFacet(MongoPageHelper<? extends PerformanceFlagged> pageHelper) {
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
