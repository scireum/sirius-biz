/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo;

import sirius.biz.analytics.metrics.MonthlyMetricComputer;
import sirius.biz.tenants.jdbc.SQLUserAccount;
import sirius.kernel.di.std.Register;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Register
public class MM2 extends MonthlyMetricComputer<SQLUserAccount> {
    @Override
    public void compute(LocalDate date,
                        LocalDateTime startOfPeriod,
                        LocalDateTime endOfPeriod,
                        boolean pastDate,
                        SQLUserAccount entity) throws Exception {
        System.out.println("hello from the other side" + entity);
    }

    @Override
    public Class<SQLUserAccount> getType() {
        return SQLUserAccount.class;
    }

    @Override
    public int getLevel() {
        return 1;
    }
}
