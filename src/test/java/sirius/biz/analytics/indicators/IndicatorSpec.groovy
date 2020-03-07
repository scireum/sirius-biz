/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.indicators

import sirius.db.mongo.Mango
import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part

class IndicatorSpec extends BaseSpecification {

    @Part
    private static Mango mango

    def "execute non-batch indicator"() {
        given:
        IndicatorTestEntity entity = new IndicatorTestEntity()

        when:
        entity.setValue("foo")
        mango.update(entity)

        then:
        entity.getIndicators().getIndications().contains("checkvalue")

        when:
        entity.setValue("bar")
        mango.update(entity)

        then:
        entity.getIndicators().getIndications().size() == 0
    }
}
