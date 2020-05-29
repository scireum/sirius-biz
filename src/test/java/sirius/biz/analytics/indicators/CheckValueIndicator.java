/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.indicators;

import sirius.kernel.di.std.Register;

@Register
public class CheckValueIndicator implements Indicator<IndicatorTestEntity> {
    @Override
    public Class<IndicatorTestEntity> getType() {
        return IndicatorTestEntity.class;
    }

    @Override
    public boolean isBatch() {
        return false;
    }

    @Override
    public boolean executeFor(IndicatorTestEntity entity) {
        return "foo".equals(entity.getValue());
    }

    @Override
    public String getName() {
        return "checkvalue";
    }
}
