/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.flags.mongo;

import sirius.biz.analytics.flags.PerformanceFlag;
import sirius.biz.analytics.flags.PerformanceFlagModifier;
import sirius.biz.analytics.flags.PerformanceFlagged;
import sirius.db.mongo.Mongo;
import sirius.db.mongo.MongoEntity;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * Provides the MongoDB implementation to modify flags stored in {@link MongoPerformanceData}.
 */
public class MongoPerformanceFlagModifier implements PerformanceFlagModifier {

    private MongoPerformanceData target;
    private Set<String> flagsToAdd = new HashSet<>();
    private Set<String> flagsToRemove = new HashSet<>();

    @Part
    private static Mongo mongo;

    protected MongoPerformanceFlagModifier(MongoPerformanceData target) {
        this.target = target;
    }

    @Override
    public PerformanceFlagModifier set(PerformanceFlag flag, boolean targetValue) {
        if (targetValue) {
            set(flag);
        } else {
            clear(flag);
        }

        return this;
    }

    @Override
    public PerformanceFlagModifier set(PerformanceFlag flag) {
        String bitIndexAsString = String.valueOf(flag.getBitIndex());
        if (!target.isSet(flag) && flagsToAdd.add(bitIndexAsString)) {
            target.flags.add(bitIndexAsString);
        }

        return this;
    }

    @Override
    public PerformanceFlagModifier clear(PerformanceFlag flag) {
        String bitIndexAsString = String.valueOf(flag.getBitIndex());
        if (target.isSet(flag) && flagsToRemove.add(bitIndexAsString)) {
            target.flags.modify().remove(bitIndexAsString);
        }

        return this;
    }

    @Override
    public void commit() {
        try {
            if (!flagsToRemove.isEmpty()) {
                mongo.update()
                     .pullAll(PerformanceFlagged.PERFORMANCE_DATA.inner(MongoPerformanceData.FLAGS),
                              flagsToRemove.toArray())
                     .executeFor((MongoEntity) target.getOwner());
            }
            if (!flagsToAdd.isEmpty()) {
                mongo.update()
                     .addEachToSet(PerformanceFlagged.PERFORMANCE_DATA.inner(MongoPerformanceData.FLAGS), flagsToAdd)
                     .executeFor((MongoEntity) target.getOwner());
            }
        } catch (Exception e) {
            Exceptions.handle()
                      .to(Log.BACKGROUND)
                      .error(e)
                      .withSystemErrorMessage("Failed to update performance flags of %s (%s): %s (%s)",
                                              target.getOwner().getIdAsString(),
                                              target.getOwner().getClass().getName())
                      .handle();
        }
    }
}
