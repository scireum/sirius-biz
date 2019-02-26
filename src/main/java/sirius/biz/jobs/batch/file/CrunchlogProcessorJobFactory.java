/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import sirius.biz.analytics.events.EventRecorder;
import sirius.biz.jobs.batch.SimpleBatchProcessJobFactory;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

/**
 * Provides a base class for migrating a directory full of crunchlog files into the EventRecorder or other analytics.
 *
 * @deprecated This is a migration job for legacy data. Use {@link EventRecorder} in favor of
 * {@link sirius.web.crunchlog.Crunchlog}.
 */
@Deprecated
public abstract class CrunchlogProcessorJobFactory extends SimpleBatchProcessJobFactory {

    @Part
    protected EventRecorder events;

    @ConfigValue("curnchlogs.path")
    private String crunchlogPath;

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return "Processing Crunchogs: " + getClass().getSimpleName();
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {

    }

    @Override
    protected void execute(ProcessContext process) throws Exception {
        File importDirectory = new File(crunchlogPath);
        process.log(ProcessLog.info()
                              .withFormattedMessage("Importing directory: %s", importDirectory.getAbsolutePath()));

        File[] files = importDirectory.listFiles();
        if (files != null) {
            Arrays.stream(files).forEach(file -> handleFile(file, process));
        }
    }

    private void handleFile(File file, ProcessContext process) {
        Watch w = Watch.start();
        process.log(ProcessLog.info()
                              .withFormattedMessage("Importing file: %s (%s)",
                                                    file.getAbsolutePath(),
                                                    NLS.formatSize(file.length())));
        try (GZIPInputStream unzippedStream = new GZIPInputStream(new FileInputStream(file))) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(unzippedStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    handleLine(line, process);
                }
            }
        } catch (IOException e) {
            process.handle(e);
        } finally {
            process.addTiming("File", w.elapsedMillis());
        }
    }

    private void handleLine(String line, ProcessContext process) {
        Watch w = Watch.start();
        try {
            handleObject(JSON.parseObject(line), process);
        } catch (Exception e) {
            process.handle(e);
        } finally {
            process.addTiming("Line", w.elapsedMillis());
        }
    }

    protected abstract void handleObject(JSONObject parseObject, ProcessContext process);
}
