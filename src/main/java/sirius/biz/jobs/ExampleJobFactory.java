/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import sirius.biz.params.IntParameter;
import sirius.biz.params.Parameter;
import sirius.biz.params.StringParameter;
import sirius.biz.process.ProcessContext;
import sirius.kernel.commons.Wait;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

@Register(classes = JobFactory.class)
public class ExampleJobFactory extends DefaultProcessJobFactory {

    @Override
    public void executeTask(ProcessContext process) {
        process.log("Hello From the");
        Wait.millis(1);
        process.warn("OTHER SIDE");
//        process.markErroneous();
        process.addTiming("Test", 10);
        process.setCurrentStateMessage("Läuft bei mir...");
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        parameterCollector.accept(new StringParameter("test", "Test").markRequired());
        parameterCollector.accept(new IntParameter("test1", "Test").markRequired());
    }

    @Nonnull
    @Override
    public String getName() {
        return "example";
    }
}
