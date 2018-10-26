/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import sirius.biz.params.Parameter;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Named;
import sirius.kernel.di.std.Priorized;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;

public interface JobFactory  extends Named, Priorized {

    String getLabel();

    @Nullable
    String getDescription();

    List<String> getRequiredPermissions();

    List<Parameter> getParameters();

    String execute(Function<String, Value> parameterProvider);
}
