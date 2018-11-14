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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TableOutput {

    private String name;
    private ProcessContext process;
    private List<String> columns;

    public TableOutput(String name, ProcessContext process, @Nullable List<String> columns) {
        this.name = name;
        this.process = process;
        this.columns = columns;
    }

    private TableOutput addMap(Map<String, String> rowAsMap) {
        process.log(new ProcessLog().withType(ProcessLogType.INFO).into(name).withContext(rowAsMap));
        return this;
    }

    public TableOutput addRow(List<Tuple<String, Cell>> data) {
        Map<String, String> rowAsMap =
                data.stream().collect(Collectors.toMap(Tuple::getFirst, t -> t.getSecond().serializeToString()));
        return addMap(rowAsMap);
    }

    public TableOutput addCells(List<Cell> data) {
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
