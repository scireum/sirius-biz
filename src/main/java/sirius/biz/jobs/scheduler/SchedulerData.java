/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.scheduler;

import sirius.biz.web.Autoloaded;
import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.kernel.commons.Strings;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contains a composite which describes the scheduling setup of an {@link SchedulerEntry}.
 * <p>
 * A composite is used so that the same values can be stored in a SQL as well as a MongoDB entity without repeating
 * too much code.
 */
public class SchedulerData extends Composite {

    /**
     * Determines if scheduling is active.
     */
    public static final Mapping ENABLED = Mapping.named("enabled");
    @Autoloaded
    private boolean enabled = true;

    /**
     * Determines the number of runs left or <tt>null</tt> to support an infinite number of executions.
     */
    public static final Mapping RUNS = Mapping.named("runs");
    @NullAllowed
    @Autoloaded
    private Integer runs;

    /**
     * Contains the pattern to determine if this task should be scheduled based on the current year.
     */
    public static final Mapping YEAR = Mapping.named("year");
    @Autoloaded
    @Length(25)
    private String year;

    /**
     * Contains the pattern to determine if this task should be scheduled based on the current month.
     */
    public static final Mapping MONTH = Mapping.named("month");
    @Autoloaded
    @Length(25)
    private String month;

    /**
     * Contains the pattern to determine if this task should be scheduled based on the current day of the month.
     */
    public static final Mapping DAY_OF_MONTH = Mapping.named("dayOfMonth");
    @Autoloaded
    @Length(100)
    private String dayOfMonth;

    /**
     * Contains the pattern to determine if this task should be scheduled based on the current day of week.
     */
    public static final Mapping DAY_OF_WEEK = Mapping.named("dayOfWeek");
    @Autoloaded
    @Length(50)
    private String dayOfWeek;

    /**
     * Contains the pattern to determine if this task should be scheduled based on the current hour of day.
     */
    public static final Mapping HOUR_OF_DAY = Mapping.named("hourOfDay");
    @Autoloaded
    @Length(50)
    private String hourOfDay;

    /**
     * Contains the pattern to determine if this task should be scheduled based on the current minute.
     */
    public static final Mapping MINUTE = Mapping.named("minute");
    @Autoloaded
    @Length(50)
    private String minute;

    /**
     * Stores the last execution of this task.
     */
    public static final Mapping LAST_EXECUTION = Mapping.named("lastExecution");
    @NullAllowed
    private LocalDateTime lastExecution;

    /**
     * Stores how often this task has been executed / scheduled.
     */
    public static final Mapping NUMBER_OF_EXECUTIONS = Mapping.named("numberOfExecutions");
    private int numberOfExecutions;

    /**
     * Contains the ID of the user on which behalf the task is executed / scheduled.
     */
    public static final Mapping USER_ID = Mapping.named("userId");
    @Length(50)
    private String userId;

    /**
     * Stores the name of the user referenced by {@link #USER_ID}.
     */
    public static final Mapping USER_NAME = Mapping.named("userName");
    @Length(150)
    private String userName;

    private static final Pattern ALL_EXPRESSION = Pattern.compile(" *\\* *");
    private static final Pattern RANGE_EXPRESSION = Pattern.compile(" *(\\d+) *- *(\\d+) *");
    private static final Pattern MODULO_EXPRESSION = Pattern.compile(" */(\\d+) *");
    private static final Pattern VALUE_EXPRESSION = Pattern.compile(" *(\\d+) *");

    @BeforeSave
    protected void validate() {
        validateField(year, "SchedulerEntry.year", this::setYear);
        validateField(month, "SchedulerEntry.month", this::setMonth);
        validateField(dayOfMonth, "SchedulerEntry.dayOfMonth", this::setDayOfMonth);
        validateField(dayOfWeek, "SchedulerEntry.dayOfWeek", this::setDayOfWeek);
        validateField(hourOfDay, "SchedulerEntry.hourOfDay", this::setHourOfDay);
        validateField(minute, "SchedulerEntry.minute", this::setMinute);
    }

    protected void validateField(String expression, String fieldKey, Consumer<String> defaultHandler) {
        try {
            expression = ensureFilled(expression, defaultHandler);
            matches(0, expression);
        } catch (IllegalArgumentException exception) {
            throw Exceptions.createHandled()
                            .withNLSKey("SchedulerEntry.invalidPatternInField")
                            .set("field", NLS.get(fieldKey))
                            .set("msg", exception.getMessage())
                            .handle();
        }
    }

    private String ensureFilled(String expression, Consumer<String> defaultHandler) {
        if (Strings.isEmpty(expression)) {
            defaultHandler.accept("*");
            return "*";
        } else {
            return expression;
        }
    }

    /**
     * Determines if this task should be executed at the given point in time.
     *
     * @param checkpoint the point in time for which the check should be performed
     * @return <tt>true</tt> if the task should be executed / scheduled, <tt>false</tt> otherwise
     */
    public boolean shouldRun(LocalDateTime checkpoint) {
        return shouldRunBasedOnGeneralSettings() && shouldRunBasedOnLastExecution(checkpoint) && shouldRunBasedOnDate(
                checkpoint) && shouldRunBasedOnTime(checkpoint);
    }

    private boolean shouldRunBasedOnGeneralSettings() {
        return enabled && (runs == null || runs > 0);
    }

    private boolean shouldRunBasedOnLastExecution(LocalDateTime checkpoint) {
        // Since the scheduler runs about every 50s, we might fire twice in the same minute - in this case,
        // we abort here. If however, an entry is to be executed in two consecutive minutes, we have to execute it
        // even if the execution interval isn't exactly 60s - otherwise we might miss an execution.
        return lastExecution == null
               || Duration.between(lastExecution, checkpoint).getSeconds() > 60
               || lastExecution.getMinute() != checkpoint.getMinute();
    }

    private boolean shouldRunBasedOnDate(LocalDateTime checkpoint) {
        return matches(checkpoint.getYear(), year)
               && matches(checkpoint.getMonthValue(), month)
               && matches(checkpoint.getDayOfMonth(), dayOfMonth)
               && matches(checkpoint.getDayOfWeek().getValue(), dayOfWeek);
    }

    private boolean shouldRunBasedOnTime(LocalDateTime checkpoint) {
        return matches(checkpoint.getHour(), hourOfDay) && matches(checkpoint.getMinute(), minute);
    }

    private boolean matches(int value, String expression) {
        if (Strings.isEmpty(expression)) {
            return true;
        }

        for (String choice : expression.split(",")) {
            if (matchesChoice(value, choice)) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesChoice(int value, String choice) {
        Matcher matcher = ALL_EXPRESSION.matcher(choice);
        if (matcher.matches()) {
            return true;
        }

        matcher = VALUE_EXPRESSION.matcher(choice);
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group(1)) == value;
        }

        matcher = RANGE_EXPRESSION.matcher(choice);
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group(1)) <= value && value <= Integer.parseInt(matcher.group(2));
        }

        matcher = MODULO_EXPRESSION.matcher(choice);
        if (matcher.matches()) {
            return value % Integer.parseInt(matcher.group(1)) == 0;
        }

        throw new IllegalArgumentException(NLS.fmtr("SchedulerEntry.invalidPattern").set("pattern", choice).format());
    }

    /**
     * Updates all internal counters once this task was executed / scheduled.
     * <p>
     * Note that this will only fill field and not persist anything to the database.
     *
     * @param timestamp the time when the task was executed
     */
    public void rememberExecution(LocalDateTime timestamp) {
        this.lastExecution = timestamp;
        this.numberOfExecutions++;
        if (this.runs != null) {
            this.runs -= 1;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public String getDayOfMonth() {
        return dayOfMonth;
    }

    public void setDayOfMonth(String dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
    }

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(String dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public String getHourOfDay() {
        return hourOfDay;
    }

    public void setHourOfDay(String hourOfDay) {
        this.hourOfDay = hourOfDay;
    }

    public String getMinute() {
        return minute;
    }

    public void setMinute(String minute) {
        this.minute = minute;
    }

    public Integer getRuns() {
        return runs;
    }

    public void setRuns(Integer runs) {
        this.runs = runs;
    }

    public LocalDateTime getLastExecution() {
        return lastExecution;
    }

    public int getNumberOfExecutions() {
        return numberOfExecutions;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}
