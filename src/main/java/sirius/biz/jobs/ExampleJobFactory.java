/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import sirius.biz.jobs.batch.DefaultBatchProcessFactory;
import sirius.biz.jobs.params.IntParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.params.StringParameter;
import sirius.biz.process.ProcessContext;
import sirius.kernel.commons.Wait;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.function.Consumer;

@Register(classes = JobFactory.class)
public class ExampleJobFactory extends DefaultBatchProcessFactory {

    @Override
    public void executeTask(ProcessContext process) throws Exception {
        process.log("Hello From the");
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
        parameterCollector.accept(new StringParameter("test", "Test").markRequired());
        parameterCollector.accept(new IntParameter("test1", "Test").markRequired());
    }

    @Nonnull
    @Override
    public String getName() {
        return "example";
    }
}
