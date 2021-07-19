/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.reports;

import sirius.kernel.commons.Tuple;
import sirius.kernel.nls.NLS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents a table consisting of {@link Cell cells}.
 * <p>
 * All data is kept in memory and therefore limited to {@link #MAX_ROWS}.
 */
public class Report {

    private static final int MAX_ROWS = 1000;

    private final List<String> labels = new ArrayList<>();
    private final List<String> columns = new ArrayList<>();
    private final List<Map<String, Cell>> rows = new ArrayList<>();

    /**
     * Adds a column to the report.
     *
     * @param name  the name of the column
     * @param label the label of the column which is {@link NLS#smartGet(String) auto translated}
     * @return the report itself for fluent method calls
     */
    public Report addColumn(String name, String label) {
        labels.add(NLS.smartGet(label));
        columns.add(name);

        return this;
    }

    /**
     * Adds a row to the report.
     *
     * @param row a list of column names and cells to add as row
     * @return <tt>true</tt> if the row was added, <tt>false</tt> if the report reached {@link #MAX_ROWS} and the row
     * was therefore rejected
     */
    public boolean addRow(List<Tuple<String, Cell>> row) {
        if (rows.size() < MAX_ROWS) {
            rows.add(row.stream().collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond)));
            return true;
        }

        return false;
    }

    /**
     * Adds a row to the report.
     *
     * @param row a list of cells to add as row (has to match the number and order of the column of this report).
     * @return <tt>true</tt> if the row was added, <tt>false</tt> if the report reached {@link #MAX_ROWS} and the row
     * was therefore rejected
     */
    public boolean addCells(Cell... row) {
        return addCells(Arrays.asList(row));
    }

    /**
     * Adds a row to the report.
     *
     * @param row a list of cells to add as row (has to match the number and order of the column of this report).
     * @return <tt>true</tt> if the row was added, <tt>false</tt> if the report reached {@link #MAX_ROWS} and the row
     * was therefore rejected
     */
    public boolean addCells(List<Cell> row) {
        if (rows.size() < MAX_ROWS) {
            Map<String, Cell> rowAsMap = new HashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                if (i < row.size()) {
                    rowAsMap.put(columns.get(i), row.get(i));
                } else {
                    rowAsMap.put(columns.get(i), new Cell(""));
                }
            }
            rows.add(rowAsMap);
            return true;
        }

        return false;
    }

    /**
     * Returns a list of all column labels.
     *
     * @return all column labels
     */
    public List<String> getLabels() {
        return Collections.unmodifiableList(labels);
    }

    /**
     * Returns a list of all column names.
     *
     * @return all column names
     */
    public List<String> getColumns() {
        return Collections.unmodifiableList(columns);
    }

    /**
     * Returns the rows in this report.
     *
     * @return the rows in this report
     */
    public List<Map<String, Cell>> getRows() {
        return Collections.unmodifiableList(rows);
    }
}
