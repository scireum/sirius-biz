/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import sirius.biz.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

@Register(classes = JobFactory.class)
public class ExampleJobFactory extends DefaultProcessJobFactory {

    @Override
    public void executeTask(ProcessContext process) {
        System.out.println("I AM HERE");
    }

    @Override
    protected void collectParameters(Consumer<Parameter> parameterCollector) {

    }

    @Nonnull
    @Override
    public String getName() {
        return "example";
    }
}
