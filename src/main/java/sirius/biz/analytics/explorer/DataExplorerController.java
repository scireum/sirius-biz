/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.explorer;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.analytics.metrics.Dataset;
import sirius.biz.jobs.batch.file.ExportXLSX;
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

import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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
    private static final String PARAM_IDENTIFIER = "identifier";

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
    private List<ChartFactory<?>> factories;

    /**
     * Provides the main UI of the chart explorer.
     *
     * @param webContext the request to handle
     */
    @Routed("/data-explorer")
    @LoginRequired
    public void explorer(WebContext webContext) {
        List<Action> actions =
                factories.stream().filter(ChartFactory::isAccessibleToCurrentUser).map(this::toAction).toList();

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
                   + provider.resolver().getAutocompleteUri()
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
    @Routed("/data-explorer/api/load-chart")
    @InternalService
    @LoginRequired
    public Future chartApi(WebContext webContext, JSONStructuredOutput output) throws Exception {
        // Some charts might take their time, therefore we don't want automatic timeouts here...
        webContext.markAsLongCall();

        String identifier = webContext.require(PARAM_IDENTIFIER).asString();
        Tuple<ChartFactory<Object>, Object> providerAndObject = resolveProviderAndObject(identifier);

        String range = webContext.require(PARAM_RANGE).asString();
        ComparisonPeriod comparisonPeriod =
                computeComparisonPeriod(webContext.require(PARAM_COMPARISON_PERIOD).asString());

        return tasks.executor(POOL_DATA_EXPLORER).fork(() -> {
            try {
                providerAndObject.getFirst()
                                 .generateOutput(providerAndObject.getSecond(),
                                                 computeStart(range),
                                                 computeEnd(range),
                                                 computeGranularity(range),
                                                 comparisonPeriod,
                                                 output);
            } catch (Exception error) {
                throw Exceptions.handle(Log.APPLICATION, error);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Tuple<ChartFactory<Object>, Object> resolveProviderAndObject(String identifier) {
        Tuple<String, String> providerAndObject = Strings.split(identifier, ":");
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

        return Tuple.create(provider, object);
    }

    /**
     * Provides a route to export charts as MS Excel file.
     *
     * @param webContext the request to respond to
     * @throws IOException in case of an IO error while writing the Excel response
     */
    @Routed("/data-explorer/export")
    public void export(WebContext webContext) throws IOException {
        String range = webContext.require(PARAM_RANGE).asString();
        TimeSeries timeSeries = new TimeSeries(computeStart(range), computeEnd(range), computeGranularity(range));
        ComparisonPeriod comparisonPeriod =
                computeComparisonPeriod(webContext.require(PARAM_COMPARISON_PERIOD).asString());

        List<Dataset> timeSeriesData = webContext.getParameters(PARAM_IDENTIFIER)
                                                 .stream()
                                                 .flatMap(identifier -> exportTimeSeriesAsDatasets(identifier,
                                                                                                   timeSeries,
                                                                                                   comparisonPeriod))
                                                 .toList();

        try (ExportXLSX export = new ExportXLSX(() -> webContext.respondWith()
                                                                .download("data-explorer.xlsx")
                                                                .outputStream(HttpResponseStatus.OK, null))) {
            export.addListRow(Stream.concat(Stream.of(NLS.get("DataExplorerController.dateColumn")),
                                            timeSeriesData.stream().map(Dataset::getLabel)).toList());
            List<String> dates = timeSeries.startDates().map(date -> timeSeries.getGranularity().format(date)).toList();
            for (int index = 0; index < dates.size(); index++) {
                final int rowIndex = index;
                export.addListRow(Stream.concat(Stream.of(dates.get(rowIndex)),
                                                timeSeriesData.stream()
                                                              .map(dataset -> dataset.getValues().get(rowIndex)))
                                        .toList());
            }
        }
    }

    private Stream<Dataset> exportTimeSeriesAsDatasets(String identifier,
                                                       TimeSeries timeSeries,
                                                       ComparisonPeriod comparisonPeriod) {

        try {
            Tuple<ChartFactory<Object>, Object> providerAndObject = resolveProviderAndObject(identifier);
            return providerAndObject.getFirst()
                                    .computeExportableTimeSeries(providerAndObject.getSecond(),
                                                                 timeSeries,
                                                                 comparisonPeriod)
                                    .stream();
        } catch (Exception error) {
            Exceptions.handle()
                      .to(Log.BACKGROUND)
                      .error(error)
                      .withSystemErrorMessage("DataExplorerController: Failed to compute time series for %s: %s (%s)",
                                              identifier)
                      .handle();

            return Stream.empty();
        }
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

    /**
     * Fetches all available charts for a given URI and target object.
     * <p>
     * This is used by <tt>t:charts</tt> to list matching charts.
     *
     * @param uri          the uri of the current page (which contains the <tt>t:charts</tt> tag
     * @param targetObject the optional target object which is being shown / processed / edited by the page
     * @return a list of identifiers and matching chart factories to show
     */
    public List<Tuple<String, ChartFactory<?>>> fetchAvailableCharts(String uri, Object targetObject) {
        List<Tuple<String, ChartFactory<?>>> result = new ArrayList<>();
        for (ChartFactory<?> factory : factories) {
            String identifier = factory.generateIdentifier(uri, targetObject);
            if (Strings.isFilled(identifier)) {
                result.add(Tuple.create(identifier, factory));
            }
        }

        return result;
    }
}
