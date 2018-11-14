/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.reports;

import sirius.kernel.commons.Tuple;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Report {

    private static final int MAX_ROWS = 1000;

    private List<String> labels = new ArrayList<>();
    private List<String> columns = new ArrayList<>();
    private List<Map<String, Cell>> rows = new ArrayList<>();

    public Report addColumn(String name, String label) {
        labels.add(label);
        columns.add(name);

        return this;
    }

    public boolean addRow(List<Tuple<String, Cell>> row) {
        if (rows.size() < MAX_ROWS) {
            rows.add(row.stream().collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond)));
            return true;
        }

        return false;
    }

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

    public List<String> getLabels() {
        return Collections.unmodifiableList(labels);
    }

    public List<String> getColumns() {
        return Collections.unmodifiableList(columns);
    }

    public List<Map<String, Cell>> getRows() {
        return Collections.unmodifiableList(rows);
    }
}
