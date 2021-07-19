/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import com.alibaba.fastjson.JSONObject;
import sirius.biz.analytics.reports.Cells;
import sirius.biz.cluster.work.DistributedTaskExecutor;
import sirius.biz.jobs.batch.ExportBatchProcessFactory;
import sirius.biz.jobs.batch.file.ExportCSV;
import sirius.biz.jobs.batch.file.ExportFileType;
import sirius.biz.jobs.batch.file.ExportXLS;
import sirius.biz.jobs.batch.file.ExportXLSX;
import sirius.biz.jobs.batch.file.LineBasedExport;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.process.output.LogsProcessOutputType;
import sirius.biz.process.output.ProcessOutput;
import sirius.biz.process.output.TableProcessOutputType;
import sirius.db.es.Elastic;
import sirius.db.es.ElasticQuery;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Responsible for exporting a {@link ProcessOutput} as MS Excel or CSV file.
 * <p>
 * At first, this might look like an overkill (running the export on a dedicated node within the restarted process).
 * But since an export might be very large - and at least for Excel we have to keep a lot (or even all) data in memory,
 * we do not want a web server node to perform this task.
 *
 * @see ProcessController#exportOutput(WebContext, String, String, String)
 */
public class ExportLogsAsFileTaskExecutor implements DistributedTaskExecutor {

    /**
     * Contains the context key used to transmit the process to export an output for.
     */
    public static final String CONTEXT_PROCESS = "process";

    /**
     * Contains the context key used to transmit the name of the output to export. Left empty to
     * export the log messages of the process.
     */
    public static final String CONTEXT_OUTPUT = "output";

    /**
     * Contains the context key used to transmit the desired export file format.
     */
    public static final String CONTEXT_FORMAT = "format";

    @Part
    @Nullable
    private Processes processes;

    @Part
    private Elastic elastic;

    @Part
    private Cells cells;

    @Part
    private TableProcessOutputType tableProcessOutputType;

    @Override
    public String queueName() {
        // We use the same queue as generic export jobs as the task is almost the same..
        return ExportBatchProcessFactory.ExportBatchProcessTaskExecutor.QUEUE_NAME;
    }

    @Override
    public void executeWork(JSONObject context) throws Exception {
        processes.execute(context.getString(CONTEXT_PROCESS), process -> executeInProcess(context, process));
    }

    private void executeInProcess(JSONObject context, ProcessContext processContext) {
        try {
            String outputName = context.getString(CONTEXT_OUTPUT);
            if (Strings.isFilled(outputName)) {
                exportSelectedOutput(context, processContext, outputName);
            } else {
                exportLogs(fetchExportFileType(context), null, processContext);
            }
            processContext.log(ProcessLog.success().withNLSKey("ExportLogsAsFileTaskExecutor.completed"));
        } catch (Exception e) {
            processContext.handle(e);
        }
    }

    private void exportSelectedOutput(JSONObject context, ProcessContext processContext, String outputName)
            throws Exception {
        ProcessOutput out = fetchOutput(processContext, outputName);

        if (TableProcessOutputType.TYPE.equals(out.getType())) {
            exportTable(fetchExportFileType(context), out, processContext);
        } else if (LogsProcessOutputType.TYPE.equals(out.getType())) {
            exportLogs(fetchExportFileType(context), out, processContext);
        } else {
            throw new IllegalArgumentException(Strings.apply(
                    "Exporting to file is only supported for logs and tables, not %s (of output %s)",
                    out.getType(),
                    out.getName()));
        }
    }

    private ProcessOutput fetchOutput(ProcessContext processContext, String outputName) {
        Process process = processes.fetchProcess(processContext.getProcessId())
                                   .orElseThrow(() -> new IllegalArgumentException("Unknown process: "
                                                                                   + processContext.getProcessId()));
        return process.getOutputs()
                      .data()
                      .stream()
                      .filter(output -> Strings.areEqual(output.getName(), outputName))
                      .findAny()
                      .orElseThrow(null);
    }

    private ExportFileType fetchExportFileType(JSONObject context) {
        return Value.of(context.get(CONTEXT_FORMAT)).getEnum(ExportFileType.class).orElse(ExportFileType.XLSX);
    }

    private void exportLogs(ExportFileType exportFileType, @Nullable ProcessOutput out, ProcessContext processContext)
            throws Exception {
        try (LineBasedExport export = createExport(processContext,
                                                   exportFileType,
                                                   out != null ? out.getLabel() : NLS.get("ProcessLog.plural"))) {
            export.addArrayRow(NLS.get("ProcessLog.type"),
                               NLS.get("ProcessLog.timestamp"),
                               NLS.get("ProcessLog.message"),
                               NLS.get("ProcessLog.messageType"),
                               NLS.get("ProcessLog.node"));
            AtomicInteger rowCount = new AtomicInteger(0);
            createLogsQuery(out, processContext).iterateAll(logEntry -> {
                writeLog(export, logEntry);
                updateExportState(processContext, rowCount.incrementAndGet(), false);
            });
            updateExportState(processContext, rowCount.get(), true);
        }
    }

    private LineBasedExport createExport(ProcessContext processContext, ExportFileType type, String name)
            throws IOException {
        String filename = name + "." + type.name().toLowerCase();
        OutputStream outputStream = processContext.addFile(filename);

        processContext.log(ProcessLog.info()
                                     .withNLSKey("ExportLogsAsFileTaskExecutor.reportTargetFile")
                                     .withContext("outputLabel", name)
                                     .withContext("filename", filename));

        if (type == ExportFileType.CSV) {
            return new ExportCSV(new OutputStreamWriter(outputStream));
        }
        if (type == ExportFileType.XLS) {
            return new ExportXLS(() -> outputStream);
        }
        if (type == ExportFileType.XLSX) {
            return new ExportXLSX(() -> outputStream);
        }

        throw new IllegalArgumentException("Unknown export type: " + type);
    }

    private ElasticQuery<ProcessLog> createLogsQuery(@Nullable ProcessOutput out, ProcessContext processContext) {
        ElasticQuery<ProcessLog> logsQuery = elastic.select(ProcessLog.class)
                                                    .eq(ProcessLog.PROCESS, processContext.getProcessId())
                                                    .eq(ProcessLog.OUTPUT, out != null ? out.getName() : null)
                                                    .orderAsc(ProcessLog.SORT_KEY);
        UserInfo user = UserContext.getCurrentUser();
        if (!user.hasPermission(ProcessController.PERMISSION_MANAGE_ALL_PROCESSES)) {
            logsQuery.eq(ProcessLog.SYSTEM_MESSAGE, false);
        }
        return logsQuery;
    }

    private void writeLog(LineBasedExport export, ProcessLog logEntry) {
        try {
            export.addArrayRow(logEntry.getType().toString(),
                               logEntry.getTimestamp(),
                               logEntry.getMessage(),
                               logEntry.getMessageType(),
                               logEntry.getNode());
        } catch (IOException e) {
            throw Exceptions.handle()
                            .to(Log.BACKGROUND)
                            .error(e)
                            .withSystemErrorMessage("An error occurred while exporting a row: %s (%s)")
                            .handle();
        }
    }

    private void exportTable(ExportFileType exportFileType, ProcessOutput out, ProcessContext processContext)
            throws Exception {
        try (LineBasedExport export = createExport(processContext, exportFileType, out.getLabel())) {
            List<String> columns = tableProcessOutputType.determineColumns(out);
            List<String> labels = tableProcessOutputType.determineLabels(out, columns);
            export.addRow(labels);
            AtomicInteger rowCount = new AtomicInteger(0);
            createLogsQuery(out, processContext).iterateAll(logEntry -> {
                writeTableRow(columns, export, logEntry);
                updateExportState(processContext, rowCount.incrementAndGet(), false);
            });
            updateExportState(processContext, rowCount.get(), true);
        }
    }

    private void writeTableRow(List<String> columns, LineBasedExport export, ProcessLog logEntry) {
        try {
            List<Object> row = columns.stream()
                                      .map(column -> cells.rawValue(logEntry.getContext().get(column).orElse(null)))
                                      .collect(Collectors.toList());
            export.addRow(row);
        } catch (IOException e) {
            throw Exceptions.handle()
                            .to(Log.BACKGROUND)
                            .error(e)
                            .withSystemErrorMessage("An error occurred while exporting a row: %s (%s)")
                            .handle();
        }
    }

    private void updateExportState(ProcessContext processContext, int currentRow, boolean lastCall) {
        if (lastCall) {
            processContext.forceUpdateState(NLS.fmtr("Process.rowsExported").set("rows", currentRow).format());
        } else {
            processContext.tryUpdateState(NLS.fmtr("Process.rowsExported").set("rows", currentRow).format());
        }
    }
}
