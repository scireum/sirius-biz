/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.search;

import com.alibaba.fastjson.JSONObject;
import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.web.BizController;
import sirius.kernel.async.Barrier;
import sirius.kernel.async.Future;
import sirius.kernel.async.Tasks;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Timeout;
import sirius.kernel.di.PartCollection;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Parts;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.web.controller.Controller;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.services.JSONStructuredOutput;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Register(classes = Controller.class)
public class OpenSearchController extends BizController {

    private static final byte[] NEWLINE = "\n".getBytes(StandardCharsets.UTF_8);
    private static final Duration SEARCH_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_RESULTS_PER_CATEGORY = 8;

    @Parts(OpenSearchProvider.class)
    private PartCollection<OpenSearchProvider> providers;

    @Part
    private Tasks tasks;

    @Routed("/tycho/search")
    @LoginRequired
    public void search(WebContext webContext) {
        webContext.respondWith().template("/templates/biz/tycho/search/search.html.pasta");
    }

    @Routed("/tycho/search/api")
    @LoginRequired
    public Future searchAPI(WebContext webContext) {
        webContext.markAsLongCall();

        String query = webContext.require("query").asString();
        if (Strings.isEmpty(query)) {
            webContext.respondWith().status(HttpResponseStatus.OK);
            return new Future().success();
        }

        Timeout timeout = new Timeout(SEARCH_TIMEOUT);
        return tasks.executor("tycho-open-search").dropOnOverload(() -> {
            JSONStructuredOutput json = webContext.respondWith().json();
            json.beginResult();
            json.property("overload", true);
            json.endResult();
        }).fork(() -> executeQuery(query, webContext, timeout));
    }

    private void executeQuery(String query, WebContext webContext, Timeout timeout) {
        OutputStream outputStream = webContext.respondWith().outputStream(HttpResponseStatus.OK, "text/plain");
        try {
            Barrier allTasksCompleted = new Barrier();

            for (OpenSearchProvider provider : providers) {
                if (provider.ensureAccess()) {
                    allTasksCompleted.add(tasks.executor("tycho-open-search-task")
                                               .dropOnOverload(() -> performSearch(query,
                                                                                   provider,
                                                                                   outputStream,
                                                                                   timeout))
                                               .fork(() -> performSearch(query, provider, outputStream, timeout)));
                }
            }

            allTasksCompleted.await();
            outputStream.write("{\"completed\":true}\n".getBytes(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Exceptions.ignore(e);
        } catch (IOException e) {
            Exceptions.ignore(e);
        } finally {
            try {
                outputStream.close();
            } catch (IOException e) {
                Exceptions.ignore(e);
            }
        }
    }

    private void performSearch(String query, OpenSearchProvider provider, OutputStream outputStream, Timeout timeout) {
        try {
            if (timeout.isReached()) {
                return;
            }

            List<OpenSearchResult> results = new ArrayList<>(MAX_RESULTS_PER_CATEGORY);
            provider.query(query, MAX_RESULTS_PER_CATEGORY, result -> {
                if (results.size() < MAX_RESULTS_PER_CATEGORY) {
                    results.add(result);
                }
            });

            if (timeout.isReached() || results.isEmpty()) {
                return;
            }

            synchronized (outputStream) {
                for (OpenSearchResult result : results) {
                    JSONObject object = new JSONObject();
                    object.put("category", provider.getLabel());
                    object.put("priority", provider.getPriority());
                    object.put("label", result.getLabel());
                    object.put("description", result.getDescription());
                    object.put("url", result.getUrl());
                    outputStream.write(object.toJSONString().getBytes(StandardCharsets.UTF_8));
                    outputStream.write(NEWLINE);
                }
                outputStream.flush();
            }
        } catch (IOException e) {
            Exceptions.ignore(e);
        } catch (Exception e) {
            Exceptions.handle()
                      .to(Log.APPLICATION)
                      .error(e)
                      .withSystemErrorMessage("Failed to execute an OpenSearchProvider (%s): %s (%s)",
                                              provider.getClass().getName())
                      .handle();
        }
    }
}
