/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.interactive;

import com.alibaba.fastjson.JSONObject;
import sirius.biz.jobs.BasicJobFactory;
import sirius.kernel.commons.Value;
import sirius.web.http.WebContext;
import sirius.web.services.JSONStructuredOutput;

import java.util.Map;
import java.util.function.Function;

public abstract class InteractiveJobFactory extends BasicJobFactory {

    @Override
    public void runInUI(WebContext request) {
        checkPermissions();
        setupTaskContext();

        Map<String, String> context = buildAndVerifyContext(request::get);
        generateResponse(request, context);
    }

    protected abstract void generateResponse(WebContext request,  Map<String, String> context);

    @Override
    protected void callInUI(WebContext request) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void runInCall(WebContext request, JSONStructuredOutput out, Function<String, Value> parameterProvider) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void runInBackground(Function<String, Value> parameterProvider) {
        throw new UnsupportedOperationException();
    }
}
