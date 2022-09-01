/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts.explorer;

import sirius.biz.web.Action;
import sirius.biz.web.BizController;
import sirius.kernel.async.Future;
import sirius.kernel.async.Tasks;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.PriorityParts;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Controller;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.services.InternalService;
import sirius.web.services.JSONStructuredOutput;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * Provides the UI of the <tt>Data-Explorer</tt>.
 * <p>
 * This renders and invokes all available {@link ChartFactory chart factories} which have been made visible to the
 * framework using {@link Register}. Note that the main route <tt>/data-explorer</tt> is available to all users of
 * the system, as the list of charts itself is filtered.
 */
@Register(classes = {Controller.class, DataExplorerController.class})
public class DataExplorerController extends BizController {

    private static final String PARAM_RANGE = "range";
    private static final String PARAM_COMPARISON_PERIOD = "comparisonPeriod";

    private static final String POOL_DATA_EXPLORER = "data-explorer";

    private static final String VALUE_LAST_YEAR = "lastYear";
    private static final String VALUE_LAST_MONTH = "lastMonth";
    private static final String VALUE_LAST_30_DAYS = "last30Days";
    private static final String VALUE_LAST_90_DAYS = "last90Days";
    private static final String VALUE_LAST_12_MONTHS = "last12Months";

    @Part
    private Tasks tasks;

    @Part
    private GlobalContext globalContext;

    @PriorityParts(ChartFactory.class)
    private List<ChartFactory<?>> providers;

    /**
     * Provides the main UI of the chart explorer.
     *
     * @param webContext the request to handle
     */
    @Routed("/data-explorer")
    @LoginRequired
    public void explorer(WebContext webContext) {
        List<Action> actions =
                providers.stream().filter(ChartFactory::isAccessibleToCurrentUser).map(this::toAction).toList();

        webContext.respondWith().template("/templates/biz/tycho/analytics/data-explorer.html.pasta", actions);
    }

    private Action toAction(ChartFactory<?> provider) {
        return new Action(provider.getLabel(),
                          determineAction(provider),
                          NLS.smartGet(provider.getCategory())).withDescription(provider.getDescription())
                                                               .withIcon(provider.getIcon());
    }

    private String determineAction(ChartFactory<?> provider) {
        if (provider.hasResolver()) {
            return "javascript:selectEntity('"
                   + provider.getName()
                   + "','"
                   + provider.resolver().autocompleteUri()
                   + "')";
        } else {
            return "javascript:addChart('" + provider.getName() + "')";
        }
    }

    /**
     * Provides a workhorse of the <tt>Data-Explorer</tt> as this is the URI which is used to fetch the actual chart
     * data.
     * <p>
     * Note that for system stability reasons, all proper requests into this URI are thrown into a separate thread
     * pool so that long-running charts don't block the main server.
     *
     * @param webContext the request to handle
     * @param output     the JSON response to generate
     * @return a future used to signal when background processing has completed
     * @throws Exception in case of any error when generating the chart
     */
    @SuppressWarnings("unchecked")
    @Routed("/data-explorer/api/load-chart")
    @InternalService
    @LoginRequired
    public Future chartApi(WebContext webContext, JSONStructuredOutput output) throws Exception {
        Tuple<String, String> providerAndObject = Strings.split(webContext.require("identifier").asString(), ":");
        ChartFactory<Object> provider = globalContext.findPart(providerAndObject.getFirst(), ChartFactory.class);

        if (!provider.isAccessibleToCurrentUser()) {
            throw Exceptions.createHandled().withDirectMessage(NLS.get("DataExplorerController.unauthorized")).handle();
        }

        Object object = provider.resolver() == null ?
                        null :
                        provider.resolver()
                                .resolve(providerAndObject.getSecond())
                                .orElseThrow(() -> Exceptions.createHandled()
                                                             .withDirectMessage(NLS.get(
                                                                     "DataExplorerController.unknownEntity"))
                                                             .handle());

        String range = webContext.require(PARAM_RANGE).asString();
        ComparisonPeriod comparisonPeriod = computeComparisonPeriod(webContext.require(PARAM_COMPARISON_PERIOD).asString());

        return tasks.executor(POOL_DATA_EXPLORER).fork(() -> {
            try {
                provider.computeData(object,
                                     computeStart(range),
                                     computeEnd(range),
                                     computeGranularity(range),
                                     comparisonPeriod,
                                     output);
            } catch (Exception e) {
                throw Exceptions.handle(Log.APPLICATION, e);
            }
        });
    }

    private ComparisonPeriod computeComparisonPeriod(String comparisonPeriod) {
        return switch (comparisonPeriod) {
            case VALUE_LAST_YEAR -> ComparisonPeriod.PREVIOUS_YEAR;
            case VALUE_LAST_MONTH -> ComparisonPeriod.PREVIOUS_MONTH;
            default -> ComparisonPeriod.NONE;
        };
    }

    private LocalDate computeStart(String range) {
        return switch (range) {
            case VALUE_LAST_30_DAYS -> LocalDate.now().minusDays(30);
            case VALUE_LAST_90_DAYS -> LocalDate.now().minusDays(90);
            case VALUE_LAST_MONTH -> LocalDate.now().minusMonths(1).with(TemporalAdjusters.firstDayOfMonth());
            case VALUE_LAST_YEAR -> LocalDate.now().minusYears(1).with(TemporalAdjusters.firstDayOfYear());
            default -> LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()).minusMonths(11);
        };
    }

    private LocalDate computeEnd(String range) {
        return switch (range) {
            case VALUE_LAST_MONTH -> LocalDate.now().minusMonths(1).with(TemporalAdjusters.lastDayOfMonth());
            case VALUE_LAST_YEAR -> LocalDate.now().minusYears(1).with(TemporalAdjusters.lastDayOfYear());
            case VALUE_LAST_12_MONTHS -> LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
            default -> LocalDate.now();
        };
    }

    private Granularity computeGranularity(String range) {
        return switch (range) {
            case VALUE_LAST_30_DAYS, VALUE_LAST_90_DAYS, VALUE_LAST_MONTH -> Granularity.DAY;
            default -> Granularity.MONTH;
        };
    }
}