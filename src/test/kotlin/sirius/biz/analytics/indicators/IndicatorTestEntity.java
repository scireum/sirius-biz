/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.indicators;

import sirius.db.mongo.MongoEntity;

public class IndicatorTestEntity extends MongoEntity implements IndicatedEntity {

    private String value;
    private final IndicatorData indicators = new IndicatorData(this);

    @Override
    public IndicatorData getIndicators() {
        return this.indicators;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
