/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import sirius.biz.analytics.reports.Cells;
import sirius.biz.jobs.batch.DefaultBatchProcessFactory;
import sirius.biz.jobs.params.IntParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.params.StringParameter;
import sirius.biz.jobs.params.TenantParameter;
import sirius.biz.jobs.params.UserAccountParameter;
import sirius.biz.process.Process;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.process.logs.ProcessLogState;
import sirius.biz.process.output.TableOutput;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Wait;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;

@Register(classes = JobFactory.class)
public class ExampleJobFactory extends DefaultBatchProcessFactory {

    @Part
    private Cells cells;

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return getLabel();
    }

    @Override
    public void executeTask(ProcessContext process) throws Exception {
        System.out.println(process.getParameter(new UserAccountParameter("test", "Test")).get().getUserAccountData().getLogin());
        TableOutput tableOutput = process.addTable("test", "Test", Arrays.asList(Tuple.create("a", "A")));
        tableOutput.addCells(Arrays.asList(cells.of("Käse hallo")));
        process.log(ProcessLog.error().withMessage("Hello From the").withState(ProcessLogState.OPEN));
        process.log(ProcessLog.warn().withMessage("Hello From the").withState(ProcessLogState.OPEN));
        process.log(ProcessLog.success().withMessage("Hello From the").withState(ProcessLogState.OPEN));
        process.log(ProcessLog.info().withMessage("Hello From the").withState(ProcessLogState.OPEN));
        Wait.millis(1);
        process.log("OTHER SIDE");
//        process.markErroneous();
        process.addTiming("Test", 10);
        process.setCurrentStateMessage("Läuft bei mir...");
        File test = File.createTempFile("xxxx", "txt");
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(test)))) {
            w.print("Hello");
        }
        process.addFile("test.txt", test);
        test.delete();
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        parameterCollector.accept(new UserAccountParameter("test", "Test").markRequired());
        parameterCollector.accept(new IntParameter("test1", "Test").markRequired());
    }

    @Override
    protected boolean hasPresetFor(Object targetObject) {
        return targetObject instanceof Process;
    }

    @Override
    protected void computePresetFor(Object targetObject, Map<String, Object> preset) {
        preset.put("test", ((Process) targetObject).getId());
    }

    @Nonnull
    @Override
    public String getName() {
        return "example";
    }

    @Override
    public String getCategory() {
        return "check";
    }
}
