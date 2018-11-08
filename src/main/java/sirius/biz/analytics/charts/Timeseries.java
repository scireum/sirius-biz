/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts;

import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.temporal.ChronoField;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Timeseries {

    private LocalDateTime requestedStart;
    private LocalDateTime requestedEnd;
    private Unit requestedUnit;
    private Unit effectiveUnit;
    private List<Interval> intervals;
    private int limit;

    public Timeseries(LocalDateTime start, LocalDateTime end, @Nullable Unit unit, int softLimit, int hardLimit) {
        this.requestedStart = start;
        this.requestedEnd = end;
        this.requestedUnit = unit;
        this.effectiveUnit = determineEffectiveUnit(softLimit, hardLimit);
        this.limit = hardLimit;
        this.intervals = createSeries();
    }

    public Timeseries(LocalDateTime start, LocalDateTime end, Unit unit, List<Interval> intervals) {
        this.requestedStart = start;
        this.requestedEnd = end;
        this.requestedUnit = unit;
        this.effectiveUnit = unit;
        this.limit = 0;
        this.intervals = intervals;
    }

    private Unit determineEffectiveUnit(int softLimit, int hardLimit) {
        Unit result = requestedUnit;
        if (result == null) {
            result = Unit.HOUR;
        }

        Duration duration = Duration.between(requestedStart, requestedEnd);
        while (result != Unit.YEAR) {
            long numberOfIntervals = duration.getSeconds() / result.getEstimatedSeconds();
            if (numberOfIntervals < softLimit || (numberOfIntervals < hardLimit && requestedUnit != null)) {
                return result;
            }
            result = Unit.values()[result.ordinal() + 1];
        }

        return result;
    }

    private List<Interval> createSeries() {
        LocalDateTime timestamp = normalizeStart();

        List<Interval> result = new ArrayList<>();
        int maxValues = limit;
        while (maxValues-- > 0 && timestamp.isBefore(requestedEnd)) {
            LocalDateTime nextTimestamp = increment(timestamp);
            result.add(new Interval(timestamp, nextTimestamp));
            timestamp = nextTimestamp;
        }

        return result;
    }

    private LocalDateTime normalizeStart() {
        LocalDateTime timestamp = requestedStart.withMinute(0).withSecond(0);
        if (effectiveUnit.ordinal() > Unit.HOUR.ordinal()) {
            timestamp = timestamp.withHour(0);
        }
        if (effectiveUnit == Unit.WEEK) {
            timestamp = timestamp.with(ChronoField.DAY_OF_WEEK, DayOfWeek.MONDAY.getValue());
        }
        if (effectiveUnit.ordinal() > Unit.WEEK.ordinal()) {
            timestamp = timestamp.withDayOfMonth(1);
        }
        if (effectiveUnit == Unit.YEAR) {
            timestamp = timestamp.withMonth(Month.JANUARY.getValue());
        }

        return timestamp;
    }

    private LocalDateTime increment(LocalDateTime timestamp) {
        switch (effectiveUnit) {
            case HOUR:
                return timestamp.plusHours(1);
            case DAY:
                return timestamp.plusDays(1);
            case WEEK:
                return timestamp.plusWeeks(1);
            case MONTH:
                return timestamp.plusMonths(1);
            case YEAR:
                return timestamp.plusYears(1);
            default:
                throw new IllegalStateException("unreachable");
        }
    }

    public LocalDateTime getRequestedStart() {
        return requestedStart;
    }

    public LocalDateTime getRequestedEnd() {
        return requestedEnd;
    }

    public Unit getRequestedUnit() {
        return requestedUnit;
    }

    public Unit getEffectiveUnit() {
        return effectiveUnit;
    }

    public List<Interval> getIntervals() {
        return Collections.unmodifiableList(intervals);
    }

    public int getLimit() {
        return limit;
    }

    public List<String> getLabels() {
        return intervals.stream().map(Interval::getStart).map(this::formatTimestamp).collect(Collectors.toList());
    }

    private String formatTimestamp(LocalDateTime timestamp) {
        switch (effectiveUnit) {
            case YEAR:
                return String.valueOf(timestamp.getYear());
            case MONTH:
                return NLS.getMonthName(timestamp.getMonthValue()) + " " + timestamp.getYear();
            case WEEK:
                return timestamp.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear())
                       + " / "
                       + timestamp.getYear();
            case DAY:
                return timestamp.getDayOfMonth() + " " + NLS.getMonthNameShort(timestamp.getMonthValue());
            case HOUR:
                return String.valueOf(timestamp.getHour());
        }

        return NLS.toUserString(timestamp);
    }

    public Timeseries computeComparisonPeriod(ComparisonPeriod period) {
        switch (period) {
            case PREVIOUS_DAY:
                return copy(date -> date.minusDays(1));
            case PREVIOUS_WEEK:
                return copy(date -> date.minusWeeks(1));
            case PREVIOUS_MONTH:
                return copy(date -> date.minusMonths(1));
            case PREVIOUS_YEAR:
                return copy(date -> date.minusYears(1));
            default:
                throw new IllegalStateException("unreachable");
        }
    }

    private Timeseries copy(Function<LocalDateTime, LocalDateTime> modifier) {
        return new Timeseries(requestedStart,
                              requestedEnd,
                              effectiveUnit,
                              intervals.stream()
                                       .map(interval -> new Interval(modifier.apply(interval.getStart()),
                                                                     modifier.apply(interval.getEnd())))
                                       .collect(Collectors.toList()));
    }
}
