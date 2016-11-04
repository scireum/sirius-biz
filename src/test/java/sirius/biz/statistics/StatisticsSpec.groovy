/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.statistics

import sirius.db.mixing.Schema
import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part

import java.time.Duration
import java.time.LocalDate

class StatisticsSpec extends BaseSpecification {

    @Part
    private static Statistics statistics;

    @Part
    private static Schema schema;

    def "statistics are incremented and aggregated as expected"() {
        given:
        schema.getReadyFuture().await(Duration.ofSeconds(45));
        and:
        StatisticalEvent evt = StatisticalEvent.create("test", AggregationLevel.DAYS);
        when:
        statistics.incrementStatistic(evt, "test");
        and:
        statistics.commitStatistics();
        then:
        statistics.getStatisticValue(evt, AggregationLevel.DAYS, "test", LocalDate.now()) == 1
        and:
        statistics.getStatisticValue(evt, AggregationLevel.MONTHS, "test", LocalDate.now()) == 1
        and:
        statistics.getStatisticValue(evt, AggregationLevel.YEARS, "test", LocalDate.now()) == 1
        and:
        statistics.getStatisticValue(evt, AggregationLevel.OVERALL, "test", LocalDate.now()) == 1
    }

    def "statistics can be incremented several times"() {
        given:
        schema.getReadyFuture().await(Duration.ofSeconds(45));
        and:
        StatisticalEvent evt = StatisticalEvent.create("test1", AggregationLevel.DAYS);
        when:
        statistics.addStatistic(evt, "test1", 2);
        and:
        statistics.commitStatistics();
        and:
        statistics.addStatistic(evt, "test1", 2);
        and:
        statistics.incrementStatistic(evt, "test1");
        and:
        statistics.commitStatistics();
        then:
        statistics.getStatisticValue(evt, AggregationLevel.DAYS, "test1", LocalDate.now()) == 5
    }

    def "statistics are deleted correctly"() {
        given:
        schema.getReadyFuture().await(Duration.ofSeconds(45));
        and:
        StatisticalEvent evt = StatisticalEvent.create("test2", AggregationLevel.DAYS);
        when:
        statistics.addStatistic(evt, "test2", 2);
        and:
        statistics.commitStatistics();
        and:
        statistics.deleteStatistic("test2")
        and:
        statistics.commitStatistics();
        then:
        statistics.getStatisticValue(evt, AggregationLevel.DAYS, "test2", LocalDate.now()) == 0
    }

}
