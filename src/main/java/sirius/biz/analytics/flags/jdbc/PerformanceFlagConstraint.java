/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.flags.jdbc;

import sirius.biz.analytics.flags.PerformanceFlag;
import sirius.biz.analytics.flags.PerformanceFlagged;
import sirius.db.jdbc.SmartQuery;
import sirius.db.jdbc.constraints.SQLConstraint;

/**
 * Provides a constraint which performs a bitwise check in order to determine if a performance flag is toggled.
 * <p>
 * {@link SQLPerformanceData} stores all performance flag as bits in a 64 bit long ({@link SQLPerformanceData#FLAGS}).
 * So in order to check if a flag is set or not, we perform a bitwise and of this bit pattern
 * ({@code 1 << flag.getBitIndex()}) and then check for 1 if we filter on a set flag and 0 to filter on a cleared flag.
 */
class PerformanceFlagConstraint extends SQLConstraint {

    private PerformanceFlag flag;
    private boolean expectedState;

    /**
     * Generates a new constraint for the given flag and expected state.
     *
     * @param flag          the flag to check
     * @param expectedState the state to filter on
     */
    PerformanceFlagConstraint(PerformanceFlag flag, boolean expectedState) {
        this.flag = flag;
        this.expectedState = expectedState;
    }

    @Override
    public void appendSQL(SmartQuery.Compiler compiler) {
        String columnName =
                compiler.translateColumnName(PerformanceFlagged.PERFORMANCE_DATA.inner(SQLPerformanceData.FLAGS));
        if (expectedState) {
            compiler.getWHEREBuilder().append(columnName).append(" & ? <> 0");
        } else {
            compiler.getWHEREBuilder().append(columnName).append(" & ? = 0");
        }
        compiler.addParameter(1L << flag.getBitIndex());
    }

    @Override
    public void asString(StringBuilder stringBuilder) {
        if (!expectedState) {
            stringBuilder.append("!");
        }
        stringBuilder.append("HAS_PERFORMANCE_FLAG(");
        stringBuilder.append(flag.getName());
        stringBuilder.append(")");
    }
}
