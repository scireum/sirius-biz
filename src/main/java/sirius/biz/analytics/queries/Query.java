/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.queries;

import sirius.biz.params.Parameter;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Named;
import sirius.web.services.JSONStructuredOutput;

import java.util.List;
import java.util.function.Function;

public interface Query extends Named {

    String getLabel();

    String getDescription();

    boolean isHidden();

    List<String> getPermissions();

    String getTargetGroup();

    List<Period> getSupportedPeriods();

    List<Parameter> getParameters();

    void generateInlineGraph(Function<String, Value> parameters, JSONStructuredOutput out);

    void generateGraph(Function<String, Value> parameters, JSONStructuredOutput out);
}
