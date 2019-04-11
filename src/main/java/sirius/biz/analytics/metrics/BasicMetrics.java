/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.metrics;

import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Mixing;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Base class which handles all the database independent boilerplate.
 */
public abstract class BasicMetrics implements Metrics {

    private static final String GLOBAL = "global";

    @Override
    public void updateFact(BaseEntity<?> target, String name, int value) {
        if (!target.isNew()) {
            updateFact(Mixing.getNameForType(target.getClass()), target.getIdAsString(), name, value);
        }
    }

    @Override
    public void updateGlobalFact(String name, int value) {
        updateFact(GLOBAL, GLOBAL, name, value);
    }

    @Override
    public void updateYearlyMetric(BaseEntity<?> target, String name, int year, int value) {
        if (!target.isNew()) {
            updateYearlyMetric(Mixing.getNameForType(target.getClass()), target.getIdAsString(), name, year, value);
        }
    }

    @Override
    public void updateGlobalYearlyMetric(String name, int year, int value) {
        updateYearlyMetric(GLOBAL, GLOBAL, name, year, value);
    }

    @Override
    public void updateMonthlyMetric(BaseEntity<?> target, String name, int year, int month, int value) {
        if (!target.isNew()) {
            updateMonthlyMetric(Mixing.getNameForType(target.getClass()),
                                target.getIdAsString(),
                                name,
                                year,
                                month,
                                value);
        }
    }

    @Override
    public void updateGlobalMonthlyMetric(String name, int year, int month, int value) {
        updateMonthlyMetric(GLOBAL, GLOBAL, name, year, month, value);
    }

    @Override
    public void updateDailyMetric(BaseEntity<?> target, String name, int year, int month, int day, int value) {
        if (!target.isNew()) {
            updateDailyMetric(Mixing.getNameForType(target.getClass()),
                              target.getIdAsString(),
                              name,
                              year,
                              month,
                              day,
                              value);
        }
    }

    @Override
    public void updateGlobalDailyMetric(String name, int year, int month, int day, int value) {
        updateDailyMetric(GLOBAL, GLOBAL, name, year, month, day, value);
    }

    @Override
    public int queryFact(BaseEntity<?> target, String name) {
        return queryFact(Mixing.getNameForType(target.getClass()), target.getIdAsString(), name);
    }

    @Override
    public int queryGlobalFact(String name) {
        return queryFact(GLOBAL, GLOBAL, name);
    }

    @Override
    public Map<String, Integer> queryFacts(BaseEntity<?> target) {
        return queryFacts(Mixing.getNameForType(target.getClass()), target.getIdAsString());
    }

    @Override
    public Map<String, Integer> queryGlobalFacts() {
        return queryFacts(GLOBAL, GLOBAL);
    }

    @Override
    public List<Integer> queryYearlyMetrics(BaseEntity<?> target, String name, LocalDate from, LocalDate to) {
        return queryYearlyMetrics(Mixing.getNameForType(target.getClass()), target.getIdAsString(), name, from, to);
    }

    @Override
    public List<Integer> queryGlobalYearlyMetrics(String name, LocalDate from, LocalDate to) {
        return queryYearlyMetrics(GLOBAL, GLOBAL, name, from, to);
    }

    @Override
    public List<Integer> queryMonthlyMetrics(BaseEntity<?> target, String name, LocalDate from, LocalDate to) {
        return queryMonthlyMetrics(Mixing.getNameForType(target.getClass()), target.getIdAsString(), name, from, to);
    }

    @Override
    public List<Integer> queryGlobalMonthlyMetrics(String name, LocalDate from, LocalDate to) {
        return queryMonthlyMetrics(GLOBAL, GLOBAL, name, from, to);
    }

    @Override
    public List<Integer> queryDailyMetrics(BaseEntity<?> target, String name, LocalDate from, LocalDate to) {
        return queryDailyMetrics(Mixing.getNameForType(target.getClass()), target.getIdAsString(), name, from, to);
    }

    @Override
    public List<Integer> queryGlobalDailyMetrics(String name, LocalDate from, LocalDate to) {
        return queryDailyMetrics(GLOBAL, GLOBAL, name, from, to);
    }
}
