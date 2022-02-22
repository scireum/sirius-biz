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
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.util.ArrayList;
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

    private final String name;
    private final ProcessContext process;
    private final List<String> columns;

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

    private TableOutput addMap(Map<String, String> rowAsMap, String messageType) {
        process.log(new ProcessLog().withType(ProcessLogType.INFO)
                                    .withMessageType(messageType)
                                    .into(name)
                                    .withContext(rowAsMap));
        return this;
    }

    /**
     * Adds a new row represented as column name and data.
     *
     * @param row         the list of columns (name and actual cell)
     * @param messageType the {@link ProcessLog#withMessageType(String) message type} which can be used for filtering
     * @return the table output itself for fluent method calls
     */
    public TableOutput addRowWithType(List<Tuple<String, Cell>> row, String messageType) {
        Map<String, String> rowAsMap =
                row.stream().collect(Collectors.toMap(Tuple::getFirst, t -> t.getSecond().serializeToString()));
        return addMap(rowAsMap, messageType);
    }

    /**
     * Adds a new row represented as column name and data.
     *
     * @param data the list of columns (name and actual cell)
     * @return the table output itself for fluent method calls
     */
    public TableOutput addRow(List<Tuple<String, Cell>> data) {
        return addRowWithType(data, null);
    }

    /**
     * Adds a new row represented as list of cells.
     * <p>
     * Note that this may only be invoked if <tt>columns</tt> was properly populated when calling the constructor.
     *
     * @param cellList    the list of cells to add
     * @param messageType the {@link ProcessLog#withMessageType(String) message type} which can be used for filtering
     * @return the table output itself for fluent method calls
     */
    public TableOutput addCellsWithType(List<Cell> cellList, String messageType) {
        if (columns == null) {
            throw new IllegalStateException("columns is null");
        }

        Map<String, String> rowAsMap = new HashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            if (i < cellList.size()) {
                rowAsMap.put(columns.get(i), cellList.get(i).serializeToString());
            } else {
                rowAsMap.put(columns.get(i), "");
            }
        }

        return addMap(rowAsMap, messageType);
    }

    /**
     * Adds a new row represented as list of cells.
     * <p>
     * Note that this may only be invoked if <tt>columns</tt> was properly populated when calling the constructor.
     *
     * @param cellList the list of cells to add
     * @return the table output itself for fluent method calls
     */
    public TableOutput addCells(List<Cell> cellList) {
        return addCellsWithType(cellList, null);
    }

    /**
     * Builder pattern to add {@link Cell} to {@link TableOutput}
     */
    public class RowBuilder {
        private final List<Cell> cells;

        private RowBuilder() {
            cells = new ArrayList<>();
        }

        /**
         * Adds a {@link Cell} to a row.
         *
         * @param cell the {@link Cell} to add
         * @return the row builder itself for fluent method calls
         */
        public RowBuilder withCell(Cell cell) {
            this.cells.add(cell);
            return this;
        }

        /**
         * Adds a plain value to a row.
         *
         * @param data the plain value to add
         * @return the row builder itself for fluent method calls
         */
        public RowBuilder withPlainCell(Object data) {
            return withCell(new Cell(NLS.toUserString(data)));
        }

        /**
         * Builds the new row as a collection of cells.
         *
         * @return the table output itself for fluent method calls
         */
        public TableOutput build() {
            return addCellsWithType(Collections.unmodifiableList(cells), null);
        }
    }

    /**
     * Initiates a {@link RowBuilder} to receive cells.
     * <p>
     * Note that this may only be invoked if <tt>columns</tt> was properly populated when calling the constructor.
     *
     * @return the table output itself for fluent method calls
     */
    public RowBuilder addRow() {
        return new RowBuilder();
    }

    /**
     * Builder pattern to add columns to a {@link TableOutput}
     */
    public static class ColumnBuilder {
        private final String name;
        private final String label;
        private final ProcessContext process;
        private final List<Tuple<String, String>> columns;

        /**
         * Creates a new ColumnBuilder to store the artefacts necessary to generate a new {@link TableOutput}
         *
         * @param process the process context where the table will be created
         * @param name  the name of the table
         * @param label the label of the table
         */
        public ColumnBuilder(ProcessContext process, String name, String label) {
            this.name = name;
            this.label = label;
            this.process = process;
            this.columns = new ArrayList<>();
        }

        /**
         * Adds a column to the table.
         *
         * @param name  the name of the column
         * @param label the label of the column to be displayed
         * @return the builder itself for fluent method calls
         */
        public ColumnBuilder withColumn(String name, String label) {
            columns.add(Tuple.create(name, label));
            return this;
        }

        /**
         * Builds the final TableOutput.
         *
         * @return the {@link TableOutput} with all columns
         */
        public TableOutput build() {
            return process.addTable(name, label, Collections.unmodifiableList(columns));
        }
    }
}
