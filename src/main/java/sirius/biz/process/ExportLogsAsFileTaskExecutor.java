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
import sirius.biz.process.output.ProcessOutput;
import sirius.biz.process.output.TableProcessOutputType;
import sirius.db.es.Elastic;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;
import sirius.web.http.WebContext;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.concurrent.atomic.AtomicInteger;

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
        // We use the same queue as generic export jobs as the task is almost the same...
        return ExportBatchProcessFactory.ExportBatchProcessTaskExecutor.QUEUE_NAME;
    }

    @Override
    public void executeWork(JSONObject context) throws Exception {
        processes.execute(context.getString(CONTEXT_PROCESS), process -> executeInProcess(context, process));
    }

    private void executeInProcess(JSONObject context, ProcessContext processContext) {
        String outputName = context.getString(CONTEXT_OUTPUT);
        ProcessOutput processOutput = Strings.isFilled(outputName) ?
                                      processContext.fetchOutput(outputName)
                                                    .orElseThrow(() -> new IllegalArgumentException(Strings.apply("Unknown output: %s",
                                                                                                    outputName))) :
                                      null;
        try (LineBasedExport export = createExport(processContext,
                                                   fetchExportFileType(context),
                                                   processOutput != null ?
                                                   processOutput.getLabel() :
                                                   NLS.get("ProcessLog.plural"))) {
            AtomicInteger rowCount = new AtomicInteger(0);
            processContext.fetchOutputEntries(outputName, (columns, labels) -> {
                try {
                    export.addListRow(labels);
                } catch (IOException e) {
                    throw Exceptions.handle()
                                    .to(Log.BACKGROUND)
                                    .error(e)
                                    .withSystemErrorMessage("An error occurred while exporting a row: %s (%s)")
                                    .handle();
                }
            }, (columns, values) -> {
                try {
                    processContext.tryUpdateState(NLS.fmtr("Process.rowsExported")
                                                     .set("rows", rowCount.incrementAndGet())
                                                     .format());
                    export.addListRow(values);
                    return true;
                } catch (IOException e) {
                    throw Exceptions.handle()
                                    .to(Log.BACKGROUND)
                                    .error(e)
                                    .withSystemErrorMessage("An error occurred while exporting a row: %s (%s)")
                                    .handle();
                }
            });

            processContext.forceUpdateState(NLS.fmtr("Process.rowsExported").set("rows", rowCount.get()).format());
        } catch (Exception e) {
            processContext.handle(e);
        }
        processContext.log(ProcessLog.success().withNLSKey("ExportLogsAsFileTaskExecutor.completed"));
    }

    private ExportFileType fetchExportFileType(JSONObject context) {
        return Value.of(context.get(CONTEXT_FORMAT)).getEnum(ExportFileType.class).orElse(ExportFileType.XLSX);
    }

    private LineBasedExport createExport(ProcessContext processContext, ExportFileType type, String name)
            throws IOException {
        String filename = Files.toSaneFileName(name).orElse("export") + "." + type.name().toLowerCase();
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
}
