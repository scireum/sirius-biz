/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics;

import sirius.db.mixing.annotations.Engine;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.Realm;

import java.time.LocalDate;

@Realm("clickhouse")
@Engine("MergeTree() PARTITION BY toYYYMM(metricDate) ORDER BY (metricDate, intHash32(target)) SAMPLE BY intHas32(target)")
public class Metric {

    private LocalDate metricDate;

    @Length(50)
    private String target;

}
