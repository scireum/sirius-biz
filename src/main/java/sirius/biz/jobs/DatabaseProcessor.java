package sirius.biz.jobs;

import sirius.db.jdbc.Row;
import sirius.db.jdbc.SQLQuery;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Values;
import sirius.web.data.LineBasedProcessor;
import sirius.web.data.RowProcessor;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Implements a base database processor class.
 * <p>
 * Transforms the selected data so that all {@link RowProcessor processors} can handle them.
 */
public class DatabaseProcessor implements LineBasedProcessor {
    /**
     * The sql query selecting the data to import.
     */
    private SQLQuery query;

    /**
     * The number of items to be handled with one query iteration.
     */
    private int limit;

    /**
     * The current limit to be used during the iteration over the query.
     */
    private Limit currentLimit;

    /**
     * Creates a new database processor
     *
     * @param query the query to iterate over
     * @param limit the number of items to be handled with one query iteration
     */
    public DatabaseProcessor(SQLQuery query, int limit) {
        this.query = query;
        this.limit = limit;
    }

    @Override
    public void run(RowProcessor rowProcessor, Predicate<Exception> errorHandler) throws Exception {
        boolean hasMore = true;

        AtomicInteger lineNumber = new AtomicInteger(0);

        int itemsToSkip = 0;
        while (hasMore) {
            currentLimit = new Limit(itemsToSkip, limit);
            try {
                hasMore = runQuery(rowProcessor, lineNumber, errorHandler);
            } catch (Exception e) {
                if (!errorHandler.test(e)) {
                    throw e;
                }
            }

            itemsToSkip += limit;
        }
    }

    private Values getHeading(Row row) {
        return Values.of(row.getFieldsList().stream().map(Tuple::getFirst).collect(Collectors.toList()));
    }

    private Values getValues(Row row) {
        return Values.of(row.getFieldsList().stream().map(Tuple::getSecond).collect(Collectors.toList()));
    }

    /**
     * Iterates over the query with the current limit.
     *
     * @param rowProcessor the row processor handling each row
     * @param lineNumber   the current line number
     * @param errorHandler the error handler called in case of an exception
     * @return <tt>true</tt> if there could be more rows to handle, if the limit would be modified, <tt>false</tt> otherwise
     * @throws SQLException may be thrown if something goes wrong with the query execution
     */
    private boolean runQuery(RowProcessor rowProcessor, AtomicInteger lineNumber, Predicate<Exception> errorHandler)
            throws SQLException {
        AtomicBoolean hasMore = new AtomicBoolean(false);

        query.iterateAll(row -> {
            try {
                // if unlimited
                hasMore.set(currentLimit.getMaxItems() > 0);

                if (lineNumber.get() < 1) {
                    rowProcessor.handleRow(lineNumber.incrementAndGet(), getHeading(row));
                }

                rowProcessor.handleRow(lineNumber.incrementAndGet(), getValues(row));
            } catch (Exception e) {
                if (!errorHandler.test(e)) {
                    throw e;
                }
            }
        }, currentLimit);

        return hasMore.get();
    }
}
