/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts;

import sirius.kernel.commons.Explain;
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
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Represents a set of {@link Interval intervals} to cover a period of time.
 *
 * @deprecated Use the {@link sirius.biz.analytics.charts.explorer.DataExplorerController Data-Explorer} for advanced
 * charts and statistics.
 */
@Deprecated
public class Timeseries {

    private final LocalDateTime requestedStart;
    private final LocalDateTime requestedEnd;
    private final Unit requestedUnit;
    private final Unit effectiveUnit;
    private final List<Interval> intervals;
    private final int limit;

    /**
     * Computes a timeseries for the given start and end date using the given unit.
     * <p>
     * If a unit is given this will try to generate less than <tt>hardLimit</tt> intervals before
     * resorting to the next unit. If no unit is given, one is chosen so that no more than <tt>softLimit</tt>
     * intervals are generated.
     *
     * @param start     the start date of the series
     * @param end       the end date of the series
     * @param unit      the unit to try to split the intervals in
     * @param softLimit the maximal desired intervals if the unit is chosen by the system
     * @param hardLimit the maximal desired intervals if a unit is specified
     */
    public Timeseries(LocalDateTime start, LocalDateTime end, @Nullable Unit unit, int softLimit, int hardLimit) {
        this.requestedStart = start;
        this.requestedEnd = end;
        this.requestedUnit = unit;
        this.effectiveUnit = determineEffectiveUnit(softLimit, hardLimit);
        this.limit = hardLimit;
        this.intervals = createSeries();
    }

    protected Timeseries(LocalDateTime start, LocalDateTime end, Unit unit, List<Interval> intervals) {
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
        return switch (effectiveUnit) {
            case HOUR -> timestamp.plusHours(1);
            case DAY -> timestamp.plusDays(1);
            case WEEK -> timestamp.plusWeeks(1);
            case MONTH -> timestamp.plusMonths(1);
            case YEAR -> timestamp.plusYears(1);
        };
    }

    /**
     * Returns the originally requested start date and time.
     *
     * @return the start date and time as requested by the caller
     */
    public LocalDateTime getRequestedStart() {
        return requestedStart;
    }

    /**
     * Returns the originally requested end date and time.
     *
     * @return the end date and time as requested by the caller
     */
    public LocalDateTime getRequestedEnd() {
        return requestedEnd;
    }

    /**
     * Returns the requested unit.
     *
     * @return the requested unit
     */
    @Nullable
    public Unit getRequestedUnit() {
        return requestedUnit;
    }

    /**
     * Returns the effective unit (length of the intervals).
     *
     * @return the computed unit which was used to generate the series
     */
    public Unit getEffectiveUnit() {
        return effectiveUnit;
    }

    /**
     * Returns all invervals of this series.
     *
     * @return the intervals of this series
     */
    public List<Interval> getIntervals() {
        return Collections.unmodifiableList(intervals);
    }

    /**
     * Returns a list of labels describing all intervals in this series.
     *
     * @return a list of labels describing this series
     */
    public List<String> getLabels() {
        return intervals.stream().map(Interval::getStart).map(this::formatTimestamp).collect(Collectors.toList());
    }

    @SuppressWarnings("squid:SwitchLastCaseIsDefaultCheck")
    @Explain("Not required as we fully handle all enum cases.")
    private String formatTimestamp(LocalDateTime timestamp) {
        return switch (effectiveUnit) {
            case YEAR -> String.valueOf(timestamp.getYear());
            case MONTH -> NLS.getMonthName(timestamp.getMonthValue()) + " " + timestamp.getYear();
            case WEEK -> timestamp.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear())
                         + " / "
                         + timestamp.getYear();
            case DAY -> timestamp.getDayOfMonth() + " " + NLS.getMonthNameShort(timestamp.getMonthValue());
            case HOUR -> String.valueOf(timestamp.getHour());
        };
    }

    /**
     * Computes a new series based on the given {@link ComparisonPeriod comparison period}.
     *
     * @param period the period which determines the offset of the generated series
     * @return the series representing the requested comparison period
     */
    public Timeseries computeComparisonPeriod(ComparisonPeriod period) {
        return switch (period) {
            case PREVIOUS_DAY -> copy(date -> date.minusDays(1));
            case PREVIOUS_WEEK -> copy(date -> date.minusWeeks(1));
            case PREVIOUS_MONTH -> copy(date -> date.minusMonths(1));
            case PREVIOUS_YEAR -> copy(date -> date.minusYears(1));
        };
    }

    private Timeseries copy(UnaryOperator<LocalDateTime> modifier) {
        return new Timeseries(requestedStart,
                              requestedEnd,
                              effectiveUnit,
                              intervals.stream()
                                       .map(interval -> new Interval(modifier.apply(interval.getStart()),
                                                                     modifier.apply(interval.getEnd())))
                                       .collect(Collectors.toList()));
    }
}
