/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process.output;

import sirius.biz.analytics.reports.Cell;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.process.logs.ProcessLogType;
import sirius.kernel.commons.Tuple;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Used to store a table as a {@link ProcessOutput} of type {@link TableProcessOutputType}.
 * <p>
 * To create an appropriate output, {@link ProcessContext#addTable(String, String, List)} can be used.
 */
public class TableOutput {

    private String name;
    private ProcessContext process;
    private List<String> columns;

    /**
     * Creates a new table output for the given output name and process.
     *
     * @param name    the name of the {@link ProcessOutput} to fill
     * @param process the context representing a {@link Process} used to store the table rows
     * @param columns a list of columns in this table, which simplifies adding new rows
     */
    public TableOutput(String name, ProcessContext process, @Nullable List<String> columns) {
        this.name = name;
        this.process = process;
        this.columns = Collections.unmodifiableList(columns);
    }

    private TableOutput addMap(Map<String, String> rowAsMap) {
        process.log(new ProcessLog().withType(ProcessLogType.INFO).into(name).withContext(rowAsMap));
        return this;
    }

    /**
     * Adds a new row represented as column name and data.
     *
     * @param data the list of columns (name and actual cell)
     * @return the table output itself for fluent method calls
     */
    public TableOutput addRow(List<Tuple<String, Cell>> data) {
        Map<String, String> rowAsMap =
                data.stream().collect(Collectors.toMap(Tuple::getFirst, t -> t.getSecond().serializeToString()));
        return addMap(rowAsMap);
    }

    /**
     * Adds a new row represented as list of cells.
     * <p>
     * Note that this may only be invoked if <tt>columns</tt> was properly populated when calling the consturctor.
     *
     * @param data the list of cells to add
     * @return the table output itself for fluent method calls
     */
    public TableOutput addCells(List<Cell> data) {
        if (columns == null) {
            throw new IllegalStateException("column is null");
        }

        Map<String, String> rowAsMap = new HashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            if (i < data.size()) {
                rowAsMap.put(columns.get(i), data.get(i).serializeToString());
            } else {
                rowAsMap.put(columns.get(i), "");
            }
        }

        return addMap(rowAsMap);
    }
}
