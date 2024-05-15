/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.search;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.web.BizController;
import sirius.kernel.async.CombinedFuture;
import sirius.kernel.async.Future;
import sirius.kernel.async.Tasks;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Json;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Timeout;
import sirius.kernel.di.PartCollection;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Parts;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.security.Permission;
import sirius.web.services.JSONStructuredOutput;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Contains the main parts of the OpenSearch engine for the Tycho UI.
 * <p>
 * The task of the OpenSearch is to search all matching entities for a given search query. As this yields quite a lot
 * of queries, we use two thread pools. This first (<tt>tycho-open-search</tt>) limits the maximal number of parallel
 * searches being executed. This is done in order to protect the system as each search invokes multiple providers
 * which each execute one or more database queries. Now as some providers will be faster than others and as we want
 * to optimize the user experience, we execute each provider in a separate thread in the second thread pool
 * (<tt>tycho-open-search-task</tt>). Now in order for all this to make sense, we're not simply generating a plain
 * JSON response as that way we'd have to wait for even the slowest provider to show any results. We rather use
 * a "comet" link which sends individual search results as generated by each provider as single JSON objects, each
 * in its own line (separated by a <tt>\n</tt>).
 * <p>
 * On the client side we use the <tt>oboe</tt> library which handles this kind of incoming JSON objects and renders
 * any incoming results as fast as possible.
 */
@Register(framework = OpenSearchController.FRAMEWORK_TYCHO_OPEN_SEARCH)
public class OpenSearchController extends BizController {

    /**
     * Contains the framework which controls of the system wide search / open search is pvoided or not.
     */
    public static final String FRAMEWORK_TYCHO_OPEN_SEARCH = "tycho.open-search";

    /**
     * Contains a readily built byte array to separate messages.
     * <p>
     * The client library expects that we sent the results as "comets" - that are JSON objects separated by a new line.
     * This way, we can immediately render the first results even while other providers are still searching for
     * possible matches.
     */
    private static final byte[] NEWLINE = "\n".getBytes(StandardCharsets.UTF_8);

    /**
     * Specifies the maximal search duration before a search is canceled.
     * <p>
     * As we want to prevent a system overload if too many or too intense searches are performed, we abort once
     * a query has been running for 5 seconds. Note that we don't necessarily interrupt any provider itself as
     * this is not supported by the JVM - we however can close the connection to the client and free up the spot
     * in the main thread pool.
     */
    private static final Duration SEARCH_TIMEOUT = Duration.ofSeconds(5);

    /**
     * To ensure that all possible results are rendered properly, we limit each provider to only 8 results at most.
     */
    private static final int MAX_RESULTS_PER_CATEGORY = 8;

    /**
     * Contains the name of the main thread pool which executes all searches.
     */
    private static final String TYCHO_OPEN_SEARCH_POOL = "tycho-open-search";

    /**
     * Contains the name of the inner thread pool which actually executes the query of a single provider. We use this
     * additional thread pool so that a slow provider does not slow down other providers.
     */
    private static final String TYCHO_OPEN_SEARCH_TASK_POOL = "tycho-open-search-task";

    private static final String PARAM_QUERY = "query";
    private static final String RESPONSE_OVERLOAD = "overload";
    private static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";
    private static final byte[] RESPONSE_COMPLETED_MESSAGE = "{\"completed\":true}\n".getBytes(StandardCharsets.UTF_8);
    private static final String RESPONSE_CATEGORY = "category";
    private static final String RESPONSE_CATEGORY_URL = "categoryUrl";
    private static final String RESPONSE_PRIORITY = "priority";
    private static final String RESPONSE_LABEL = "label";
    private static final String RESPONSE_HTML_DESCRIPTION = "htmlDescription";
    private static final String RESPONSE_URL = "url";

    @Parts(OpenSearchProvider.class)
    private PartCollection<OpenSearchProvider> providers;

    @Part
    private Tasks tasks;

    /**
     * Renders the search UI.
     *
     * @param webContext the request to handle.
     */
    @Routed("/open-search")
    @LoginRequired
    @Permission("permission-open-search")
    public void search(WebContext webContext) {
        webContext.respondWith()
                  .template("/templates/biz/tycho/search/search.html.pasta", webContext.get(PARAM_QUERY).asString());
    }

    /**
     * Actually performs the search passed in as <b>query</b> and yields a comet response.
     *
     * @param webContext the request to handle.
     * @return a future so that the framework know when to release the underlying connection and resources
     */
    @Routed("/open-search/api")
    @LoginRequired
    @Permission("permission-open-search")
    public Future searchAPI(WebContext webContext) {
        webContext.markAsLongCall();

        String query = webContext.require(PARAM_QUERY).toLowerCase();
        if (Strings.isEmpty(query)) {
            webContext.respondWith().status(HttpResponseStatus.OK);
            return new Future().success();
        }

        Timeout timeout = new Timeout(SEARCH_TIMEOUT);
        return tasks.executor(TYCHO_OPEN_SEARCH_POOL).dropOnOverload(() -> {
            JSONStructuredOutput json = webContext.respondWith().json();
            json.beginResult();
            json.property(RESPONSE_OVERLOAD, true);
            json.endResult();
        }).fork(() -> executeQuery(query, webContext, timeout));
    }

    private void executeQuery(String query, WebContext webContext, Timeout timeout) {
        OutputStream outputStream =
                webContext.respondWith().outputStream(HttpResponseStatus.OK, CONTENT_TYPE_APPLICATION_JSON);
        try {
            CombinedFuture allTasksCompleted = new CombinedFuture();

            for (OpenSearchProvider provider : providers) {
                if (provider.ensureAccess()) {
                    allTasksCompleted.add(tasks.executor(TYCHO_OPEN_SEARCH_TASK_POOL)
                                               .dropOnOverload(() -> performSearch(query,
                                                                                   provider,
                                                                                   outputStream,
                                                                                   timeout))
                                               .fork(() -> performSearch(query, provider, outputStream, timeout)));
                }
            }

            allTasksCompleted.asFuture().await(SEARCH_TIMEOUT);
            outputStream.write(RESPONSE_COMPLETED_MESSAGE);
        } catch (IOException exception) {
            Exceptions.ignore(exception);
        } finally {
            try {
                outputStream.close();
            } catch (IOException exception) {
                Exceptions.ignore(exception);
            }
        }
    }

    @SuppressWarnings("java:S2445")
    @Explain("We actually have to synchronize on this output stream as otherwise the response might be messed up.")
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
                    outputStream.write(Json.write(transformToJson(provider, result)).getBytes(StandardCharsets.UTF_8));
                    outputStream.write(NEWLINE);
                }
                outputStream.flush();
            }
        } catch (IOException exception) {
            Exceptions.ignore(exception);
        } catch (Exception exception) {
            Exceptions.handle()
                      .to(Log.APPLICATION)
                      .error(exception)
                      .withSystemErrorMessage("Failed to execute an OpenSearchProvider (%s): %s (%s)",
                                              provider.getClass().getName())
                      .handle();
        }
    }

    private ObjectNode transformToJson(OpenSearchProvider provider, OpenSearchResult result) {
        ObjectNode object = Json.createObject();
        object.put(RESPONSE_CATEGORY, provider.getLabel());
        object.put(RESPONSE_CATEGORY_URL, provider.getUrl());
        object.put(RESPONSE_PRIORITY, provider.getPriority());
        object.put(RESPONSE_LABEL, result.getLabel());
        object.put(RESPONSE_HTML_DESCRIPTION, result.getHtmlDescription());
        object.put(RESPONSE_URL, result.getUrl());
        return object;
    }
}
